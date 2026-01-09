// 수정됨: (B안 진단 + 트래픽 절감 + 원인 분리)
//        (1) refineOnce()를 "도착정보(arrival) 관측/진단" 중심으로 재구성: empty / routeId매칭실패 / arrtime누락 / diff<=0 / budget/limitHit / apiError / dbFatalStop
//        (2) arrival API 원문(JSON/텍스트) 1개만으로 (쿼터/인증/파라미터/응답구조/arrtime부재) 원인 확정 가능하도록, 최초 1회 원문 샘플을 diag에 저장
//        (3) 트래픽 절감: refineOnce(calls)는 "세그먼트 수"가 아니라 "arrival API 호출 예산(callsBudget)"으로 해석하여, 예산 소진 시 즉시 조기중단
//        (4) items/item 경로가 없거나 비정상 타입일 때를 apiError로 잡지 않고 arrivalItemsEmpty로 분리(응답구조/빈 결과 케이스를 명확히 카운트)
//        (5) 쿼터 소진 감지(HTTP 429 / resultMsg) 시 ApiQuotaManager를 즉시 소진 처리하고 collectorSwitch OFF로 내려 수집 루프를 종료
//        (6) route-stops / route-noList 등 수집 루프에서 발생하는 외부 호출에도 전역 쿼터를 적용하고, 429/쿼터 초과 메시지면 즉시 중단

package com.example.demo.collector;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BusSegmentCollector {

    // =========================
    // 운영 모드 스위치(핵심)
    // =========================

    // ✅ 1차(Seed) 모드: true면 arrival API를 전혀 호출하지 않고 거리 기반만으로 적재한다.
    //    - 지금 "DJB30300001만 늘어나는" 문제를 가장 빠르게 끝내는 스위치
    //    - 전체 route_id가 DB에 들어가고 순환이 눈으로 확인되면 false로 바꿔서 2차(Refine) 수행
    private static final boolean SEED_DISTANCE_ONLY = false;

    // ✅ 2차(Refine) 모드에서 arrival API 호출을 너무 많이 하지 않도록 노선당 호출 상한
    //    - 상한을 넘으면 해당 노선은 남은 구간을 거리 기반 fallback으로 처리한다(멈춤 방지)
    // ✅ 트래픽 최소화 최우선: 노선당 arrival API 호출 상한을 강하게 낮춘다.
    //    - 기존 80은 "성과(=arrivalUsed) 0"인 노선에서도 쿼터를 태우는 최악의 패턴이었다.
    //    - 이 값은 '정확도'보다 '호출 최소화'를 우선하는 정책값이다.
    private static final int ARRIVAL_API_LIMIT_PER_ROUTE = 1000; // ✅ High Traffic: 1000 (Unlimited/All Stops)

    // =========================
    // TAGO 기본 설정
    // =========================
    private static final String CITY_CODE_DAEJEON = "25";

    // ✅ 사용자 서비스 키(요청에 따라 그대로 사용)
    private static final String SERVICE_KEY =
        "ff623cef3aa0e011104003d8973105076b9f4ce098a93e4b6de36a9f2560529c";

    private static final String TAGO_ROUTE_BASE_URL   = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService";
    private static final String TAGO_ARRIVAL_BASE_URL = "http://apis.data.go.kr/1613000/ArvlInfoInqireService";

    private static final String OP_GET_ROUTE_NO_LIST    = "/getRouteNoList";
    private static final String OP_GET_ROUTE_STOPS      = "/getRouteAcctoThrghSttnList";
    private static final String OP_GET_ARRIVAL_BY_STTN  = "/getSttnAcctoArvlPrearngeInfoList";

    // ✅ API 호출 간 텀(과도한 호출 방지)
    private static final int API_SLEEP_MS = 120;

    // =========================
    // fallback 시간 계산 파라미터
    // =========================
    private static final double BUS_FALLBACK_SPEED_KMH = 20.0;
    private static final int MIN_TRAVEL_SEC = 15;
    private static final int MAX_TRAVEL_SEC = 1800;

    // =========================
    // 라운드로빈 상태(메모리)
    // =========================
    private static final AtomicInteger ROUND_ROBIN_INDEX = new AtomicInteger(0);
    // 전체 노선 1바퀴(모든 routeId를 1회씩 처리) 완료 횟수
    // - VISITED_ROUTES가 routes.size()에 도달하여 clear()될 때 1 증가한다.
    private static final AtomicInteger CYCLE_COUNT = new AtomicInteger(0);
    private static final Set<String> VISITED_ROUTES = ConcurrentHashMap.newKeySet();

    // =========================
    // 최근 collectOnce에서 처리한 routeId 목록(Refine 단계에서 재사용)
    // =========================
    // - CollectorController가 collectOnce() 다음 refineOnce()를 호출할 때,
    //   같은 루프에서 처리한 노선들만 대상으로 arrtime 샘플을 누적하기 위한 상태이다.
    // - route-stops는 ROUTE_STOPS_CACHE가 잡고 있으므로 추가 트래픽은 arrival API만 발생한다.
    // - volatile로 두어 다른 스레드에서도 최신 참조를 보게 한다(단일 스레드 운영이지만 안전하게).
    private static volatile List<String> LAST_TARGET_ROUTE_IDS = Collections.emptyList();

    // =========================
    // route-stops 캐시(핵심)
    // =========================
    // routeId -> 정류장 목록(서버 생명주기 동안 1회 호출 후 재사용)
    // - 빈/실패 결과도 캐시에 저장하여 같은 노선에 대한 재호출을 방지한다.
    private static final Map<String, List<StopOnRoute>> ROUTE_STOPS_CACHE = new ConcurrentHashMap<>();

    // ✅ DB 풀 종료를 한 번이라도 감지하면, 이후 모든 작업을 즉시 멈추기 위한 치명 상태 플래그
    private static final AtomicBoolean DB_CLOSED_FATAL = new AtomicBoolean(false);

    @Autowired
    private CollectorSwitch collectorSwitch;

    /**
     * 현재 수집이 중단 요청(토글 OFF)되었거나 스레드 인터럽트가 걸렸는지 확인한다.
     * + DB 풀 종료 치명 상태(DB_CLOSED_FATAL)면 무조건 중단한다.
     */
    private boolean shouldStopNow() {
        // ✅ 전역 일일 쿼터가 0이면, 즉시 수집을 멈춘다(무한 루프/로그 스팸 방지)
        if (apiQuotaManager != null && apiQuotaManager.isExhaustedToday()) {
            if (collectorSwitch != null) {
                collectorSwitch.stop();
            }
            return true;
        }
        if (DB_CLOSED_FATAL.get()) {
            return true;
        }
        if (collectorSwitch != null && !collectorSwitch.isEnabled()) {
            return true;
        }
        return Thread.currentThread().isInterrupted();
    }

    /**
     * 외부 API에서 "오늘 쿼터 소진"이 확정된 경우, 로컬 쿼터를 0으로 만들고 수집 스위치를 OFF로 내려
     * 수집 루프가 즉시 종료되도록 만든다.
     */
    private void stopCollectorDueToQuota(String meta, String raw) {
        try {
            if (apiQuotaManager != null) {
                apiQuotaManager.markExhaustedForToday();
            }
        } catch (Exception ignore) {
        }

        try {
            if (collectorSwitch != null) {
                collectorSwitch.stop();
            }
        } catch (Exception ignore) {
        }

        System.out.println("[COLLECTOR][QUOTA] exhausted -> collector stopped. meta=" + meta);
        if (raw != null && !raw.isBlank()) {
            // 원문은 너무 길 수 있으니 앞부분만 로그로 남긴다.
            String s = raw;
            if (s.length() > 400) {
                s = s.substring(0, 400) + "...";
            }
            System.out.println("[COLLECTOR][QUOTA] rawSample=" + s);
        }
    }

    private boolean isQuotaExceededMessage(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        // 공공데이터포털/기관 API에서 자주 보이는 패턴들
        if (m.contains("quota") && m.contains("exceed")) return true;
        if (m.contains("token") && m.contains("quota") && m.contains("exceed")) return true;
        // 한글 메시지(예: "일일 호출 허용량을 초과")
        if (msg.contains("허용량") && msg.contains("초과")) return true;
        if (msg.contains("호출") && msg.contains("초과")) return true;
        return false;
    }

    @Autowired
    private DataSource dataSource;

    // ✅ 전역 호출 예산(일일 10,000) 강제 적용
    //    - arrival API 호출은 반드시 tryConsume(1) 성공한 경우에만 수행한다.
    //    - 예산 소진 시: 즉시 null 반환 → fallback(거리 기반)으로 진행되며, 추가 호출은 발생하지 않는다.
    @Autowired
    private ApiQuotaManager apiQuotaManager;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void collectOnce() {
        collectOnce(5);
    }

    // =========================
    // B안: Refine 진단 구조체
    // =========================
    private static class RefineDiag {
        private final int callsBudget;  // arrival API 호출 예산(전체)
        private int callsUsed;          // 실제 arrival API 호출 수(전체)
        private int arrivalOk;          // arrtime을 정상 획득한 횟수(=fetchArrivalSeconds가 arrtime>0 반환)
        private int arrivalItemsEmpty;  // items/item 경로가 없거나 타입 불일치(=실질적으로 빈 결과/구조 불일치)
        private int routeIdMatchFail;   // items는 있는데 routeId가 매칭되는 항목이 없음
        private int arrtimeMissing;     // routeId는 매칭되는데 arrtime이 없거나 0
        private int diffNonPositive;    // toArr - fromArr <= 0
        private int limitHit;           // callsBudget 소진 또는 per-route 제한으로 조기 차단된 횟수
        private int dbFatalStop;        // DB 치명 상태로 중단된 횟수
        private int apiError;           // HTTP/파싱 예외 또는 resultCode!=00 등 "진짜 에러" 횟수

        // 원문 샘플(최초 1회만 저장) - 원인 확정용
        private String lastArrivalRaw;  // JSON/텍스트 그대로
        private Boolean lastArrivalOk;  // 해당 샘플이 "정상(00)"이었는지
        private String lastArrivalMeta; // header/resultMsg 또는 예외 메시지 등 짧은 메타

        private RefineDiag(int callsBudget) {
            this.callsBudget = Math.max(0, callsBudget);
        }

        private boolean budgetExhausted() {
            return callsBudget > 0 && callsUsed >= callsBudget;
        }

        private void recordRawOnce(String raw, Boolean ok, String meta) {
            // 원문(raw)은 최초 1회만 저장한다.
            if (this.lastArrivalRaw == null && raw != null) {
                this.lastArrivalRaw = raw;
            }

            // ok/meta는 raw가 없더라도(예: exception) 최초 1회만 보조 정보로 남긴다.
            if (this.lastArrivalOk == null && ok != null) {
                this.lastArrivalOk = ok;
            }
            if (this.lastArrivalMeta == null && meta != null && !meta.isBlank()) {
                this.lastArrivalMeta = meta;
            }
        }
    }

    /**
     * ✅ B안 refineOnce
     * - calls: "세그먼트 개수"가 아니라 "arrival API 호출 예산(callsBudget)"이다.
     * - 예산이 소진되면 즉시 조기 중단하여 트래픽 폭발을 막는다.
     */
    public void refineOnce(int calls) {
        if (shouldStopNow()) {
            System.out.println("[COLLECTOR] refineOnce stop requested");
            return;
        }

        if (calls <= 0) {
            System.out.println("[COLLECTOR] refineOnce calls must be > 0");
            return;
        }

        List<String> targetRouteIds = LAST_TARGET_ROUTE_IDS;
        if (targetRouteIds == null || targetRouteIds.isEmpty()) {
            System.out.println("[COLLECTOR] refineOnce: no last target routes. skip.");
            return;
        }

        RefineDiag diag = new RefineDiag(calls);

        int refined = 0;
        int skippedNoTime = 0;

        for (String routeId : targetRouteIds) {
            if (shouldStopNow()) {
                System.out.println("[COLLECTOR] refineOnce stop requested (mid-loop)");
                break;
            }
            if (diag.budgetExhausted()) {
                break;
            }
            if (isBlank(routeId)) continue;

            Map<String, Integer> arrivalCache = new HashMap<>();
            int[] arrivalApiCalls = new int[] { 0 };

            try {
                if (shouldStopNow()) break;

                List<StopOnRoute> stops = fetchStopsByRoute(routeId);
                if (stops == null || stops.size() < 2) {
                    continue;
                }

                Map<Integer, List<StopOnRoute>> stopsByDir = new HashMap<>();
                for (StopOnRoute s : stops) {
                    if (s == null) continue;
                    int dir = s.updowncd;
                    stopsByDir.computeIfAbsent(Integer.valueOf(dir), k -> new ArrayList<>()).add(s);
                }

                for (Map.Entry<Integer, List<StopOnRoute>> e : stopsByDir.entrySet()) {
                    if (diag.budgetExhausted()) break;
                    if (shouldStopNow()) {
                        System.out.println("[COLLECTOR] refineOnce stop requested (mid-loop/dir)");
                        break;
                    }

                    Integer dirObj = e.getKey();
                    int dir = (dirObj == null ? 0 : dirObj.intValue());
                    List<StopOnRoute> dirStops = e.getValue();
                    if (dirStops == null || dirStops.size() < 2) continue;

                    dirStops.sort(Comparator.comparingInt(s -> s.routeSeq));

                    for (int i = 0; i < dirStops.size() - 1; i++) {
                        if (diag.budgetExhausted()) break;
                        if (shouldStopNow()) {
                            System.out.println("[COLLECTOR] refineOnce stop requested (mid-loop/segment)");
                            break;
                        }

                        StopOnRoute from = dirStops.get(i);
                        StopOnRoute to = dirStops.get(i + 1);
                        if (from == null || to == null) continue;

                        double distanceM = haversineMeters(from.lat, from.lng, to.lat, to.lng);

                        Integer travelSecSample = estimateTravelSecondsByArrivalDiffCached(
                            routeId,
                            from.nodeId,
                            to.nodeId,
                            arrivalCache,
                            arrivalApiCalls,
                            diag
                        );

                        if (travelSecSample == null || travelSecSample <= 0) {
                            skippedNoTime++;
                            continue;
                        }

                        boolean ok = upsertSegmentWeightBus(
                            routeId,
                            dir,
                            from,
                            to,
                            distanceM,
                            travelSecSample
                        );

                        if (ok) {
                            refined++;
                        }

                        // refineOnce는 "DB upsert 횟수"가 아니라 "arrival API 호출 예산"이 핵심이므로
                        // 여기서는 remaining 같은 세그먼트 기반 카운트를 사용하지 않는다.
                    }
                }

            } catch (Exception ex) {
                // refineOnce 내부 로직에서 예외가 나면 apiError로 잡되, 원문 샘플이 없으면 메시지를 남긴다.
                diag.apiError++;
                diag.recordRawOnce(null, null, ex.getMessage());
                System.out.println("[COLLECTOR][ERROR] refineOnce routeId=" + routeId + " msg=" + ex.getMessage());
            }
        }

        // ✅ 진단 한 줄 출력(사용자 요구: 상태에서 원인 분리)
        System.out.println(
            "[COLLECTOR] refineOnce done. calls=" + calls +
            " refined=" + refined +
            " skippedNoTime=" + skippedNoTime +
            " arrivalItemsEmpty=" + diag.arrivalItemsEmpty +
            " routeIdMatchFail=" + diag.routeIdMatchFail +
            " arrtimeMissing=" + diag.arrtimeMissing +
            " diffNonPositive=" + diag.diffNonPositive +
            " limitHit=" + diag.limitHit +
            " dbFatalStop=" + diag.dbFatalStop +
            " apiError=" + diag.apiError +
            " callsUsed=" + diag.callsUsed +
            " callsBudget=" + diag.callsBudget
        );

        // ✅ 원문 샘플이 있으면 1회만 출력(너무 길면 잘라서)
        if (diag.lastArrivalRaw != null) {
            String raw = diag.lastArrivalRaw;
            String cut = raw;
            int max = 900;
            if (raw.length() > max) {
                cut = raw.substring(0, max) + "...(truncated)";
            }
            System.out.println(
                "[COLLECTOR] LAST_ARRIVAL_RAW ok=" + diag.lastArrivalOk +
                " meta=" + diag.lastArrivalMeta +
                " raw=" + cut
            );
        }
    }

    public void collectOnce(int batchSize) {
        if (shouldStopNow()) {
            System.out.println("[COLLECTOR] collectOnce stop requested");
            return;
        }

        if (batchSize <= 0) {
            System.out.println("[COLLECTOR] batchSize must be > 0");
            return;
        }

        List<RouteInfo> routes = fetchAllRoutes();
        if (routes.isEmpty()) {
            System.out.println("[COLLECTOR] route list is empty. stop.");
            return;
        }

        if (VISITED_ROUTES.size() >= routes.size()) {
            System.out.println("[COLLECTOR] cycle complete (visited=" + VISITED_ROUTES.size() + "/" + routes.size() + "). reset visited.");
            CYCLE_COUNT.incrementAndGet();
            VISITED_ROUTES.clear();
        }

        List<RouteInfo> targetRoutes = selectRoundRobinSlice(routes, batchSize);

        List<String> lastIds = new ArrayList<>(targetRoutes.size());
        for (RouteInfo r : targetRoutes) {
            if (r != null && !isBlank(r.routeId)) {
                lastIds.add(r.routeId);
            }
        }
        LAST_TARGET_ROUTE_IDS = Collections.unmodifiableList(lastIds);

        StringBuilder targetIds = new StringBuilder();
        for (int i = 0; i < targetRoutes.size(); i++) {
            if (i > 0) targetIds.append(", ");
            targetIds.append(targetRoutes.get(i).routeId);
        }
        System.out.println("[COLLECTOR] targetRouteIds=" + targetIds);

        System.out.println("[COLLECTOR] routes(total)=" + routes.size()
            + ", target(batchSize)=" + targetRoutes.size()
            + ", rrIndex(now)=" + ROUND_ROBIN_INDEX.get()
            + ", visited(now)=" + VISITED_ROUTES.size()
            + ", seedDistanceOnly=" + SEED_DISTANCE_ONLY
            + ", routeStopsCacheSize(now)=" + ROUTE_STOPS_CACHE.size());

        // ✅ 병렬 처리를 위한 AtomicInteger 카운터 사용
        AtomicInteger totalSegmentsUpserted = new AtomicInteger(0);
        AtomicInteger totalSegmentsSkippedNoTime = new AtomicInteger(0);
        AtomicInteger totalSegmentsTried = new AtomicInteger(0);

        // ✅ 순차 루프(for) -> 병렬 스트림(parallelStream)으로 변경하여 동시 실행
        //    (Batch=2 이상일 때 진짜 병렬로 동시에 API를 호출함)
        targetRoutes.parallelStream().forEach(route -> {
            if (shouldStopNow()) {
                // 병렬 스트림에서는 break 대신 return으로 종료
                return;
            }
            long routeStartMs = System.currentTimeMillis();
            System.out.println("[COLLECTOR] routeStart=" + route.routeId);

            Map<String, Integer> arrivalCache = new HashMap<>();
            int[] arrivalApiCalls = new int[] { 0 };

            try {
                if (shouldStopNow()) return;

                List<StopOnRoute> stops = fetchStopsByRoute(route.routeId);
                if (stops.size() < 2) {
                    System.out.println("[COLLECTOR] routeId=" + route.routeId + " stops < 2. skip.");
                    return; // continue -> return
                }


                Map<Integer, List<StopOnRoute>> stopsByDir = new HashMap<>();
                for (StopOnRoute s : stops) {
                    int dir = (s == null) ? 0 : s.updowncd;
                    stopsByDir.computeIfAbsent(dir, k -> new ArrayList<>()).add(s);
                }

                // int upsertedForRoute = 0; // Replaced by AtomicInteger
                // int skippedForRouteNoTime = 0; // Replaced by AtomicInteger
                // int triedForRoute = 0; // Replaced by AtomicInteger

                int arrivalUsed = 0;
                int fallbackUsed = 0;

                for (Map.Entry<Integer, List<StopOnRoute>> e : stopsByDir.entrySet()) {
                    if (shouldStopNow()) {
                        System.out.println("[COLLECTOR] collectOnce stop requested (mid-loop/dir)");
                        break;
                    }

                    Integer dir = e.getKey();
                    List<StopOnRoute> dirStops = e.getValue();

                    if (dirStops == null || dirStops.size() < 2) {
                        continue;
                    }

                    dirStops.sort(Comparator.comparingInt(s -> s.routeSeq));

                    for (int i = 0; i < dirStops.size() - 1; i++) {
                        if (shouldStopNow()) {
                            System.out.println("[COLLECTOR] collectOnce stop requested (mid-loop/segment)");
                            break;
                        }

                        StopOnRoute from = dirStops.get(i);
                        StopOnRoute to   = dirStops.get(i + 1);

                        double distanceM = haversineMeters(from.lat, from.lng, to.lat, to.lng);

                        Integer travelSecSample;

                        if (SEED_DISTANCE_ONLY) {
                            travelSecSample = estimateTravelSecondsByDistanceFallback(distanceM);
                            fallbackUsed++;
                        } else {
                            // collectOnce는 "대량 수집/적재"가 목적이므로,
                            // 여기서는 기존처럼 per-route 상한(ARRIVAL_API_LIMIT_PER_ROUTE)까지만 호출하고
                            // 실패 시 거리 fallback으로 진행한다.
                            travelSecSample = estimateTravelSecondsByArrivalDiffCached(
                                route.routeId,
                                from.nodeId,
                                to.nodeId,
                                arrivalCache,
                                arrivalApiCalls,
                                null // ✅ collectOnce에서는 diag 집계하지 않음
                            );
                            totalSegmentsTried.incrementAndGet(); // Increment here as API call is attempted

                            if (travelSecSample != null && travelSecSample > 0) {
                                arrivalUsed++;
                            }

                            // ✅ [Modified] Fallback disabled for data purity.
                            // If API returns no data (null or <=0), we do NOT use distance-based estimate.
                            // This ensures DB only contains real-time data.
                            if (travelSecSample == null || travelSecSample <= 0) {
                                // travelSecSample = estimateTravelSecondsByDistanceFallback(distanceM);
                                // fallbackUsed++;
                            }
                        }

                        if (travelSecSample == null || travelSecSample <= 0) {
                            totalSegmentsSkippedNoTime.incrementAndGet(); // Use AtomicInteger
                            continue;
                        }

                        boolean ok = upsertSegmentWeightBus(
                            route.routeId,
                            dir != null ? dir.intValue() : 0,
                            from,
                            to,
                            distanceM,
                            travelSecSample
                        );

                        // triedForRoute++; // Replaced by totalSegmentsTried.incrementAndGet() above
                        if (ok) {
                            totalSegmentsUpserted.incrementAndGet(); // Use AtomicInteger
                        }
                    }
                }

                // totalSegmentsUpserted += upsertedForRoute; // Replaced by direct AtomicInteger updates
                // totalSegmentsSkippedNoTime += skippedForRouteNoTime; // Replaced by direct AtomicInteger updates
                // totalSegmentsTried += triedForRoute; // Replaced by direct AtomicInteger updates

                System.out.println("[COLLECTOR] routeId=" + route.routeId
                    // + " tried=" + triedForRoute // Removed as it's now global
                    // + " upserted=" + upsertedForRoute // Removed as it's now global
                    // + " skippedNoTime=" + skippedForRouteNoTime // Removed as it's now global
                    + " arrivalUsed=" + arrivalUsed
                    + " fallbackUsed=" + fallbackUsed
                    + " arrivalApiCalls=" + arrivalApiCalls[0]
                    + " arrivalCacheSize=" + arrivalCache.size());

                long elapsedMs = System.currentTimeMillis() - routeStartMs;
                System.out.println("[COLLECTOR] routeEnd=" + route.routeId + " elapsedMs=" + elapsedMs);

            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - routeStartMs;
                System.out.println("[COLLECTOR][ERROR] routeId=" + route.routeId + " msg=" + e.getMessage());
                System.out.println("[COLLECTOR] routeEnd=" + route.routeId + " elapsedMs=" + elapsedMs + " (error)");
            }
        }); // End of parallelStream

        if (VISITED_ROUTES.size() >= routes.size()) {
            System.out.println("[COLLECTOR] ALL ROUTES VISITED (visited=" + VISITED_ROUTES.size() + "/" + routes.size() + "). next call will start new cycle.");
        }

        System.out.println("[COLLECTOR] done. routes=" + targetRoutes.size()
            + ", totalSegmentsUpserted=" + totalSegmentsUpserted
            + ", totalSkippedNoTime=" + totalSegmentsSkippedNoTime);
    }

    private List<RouteInfo> selectRoundRobinSlice(List<RouteInfo> routes, int batchSize) {
        int n = routes.size();
        int start = ROUND_ROBIN_INDEX.get();

        List<RouteInfo> slice = new ArrayList<>(Math.min(batchSize, n));

        int idx = start;
        int scanned = 0;

        while (scanned < n && slice.size() < batchSize) {
            if (shouldStopNow()) {
                break;
            }

            RouteInfo r = routes.get(idx);

            if (r != null && !isBlank(r.routeId)) {
                if (!VISITED_ROUTES.contains(r.routeId)) {
                    VISITED_ROUTES.add(r.routeId);
                    slice.add(r);
                }
            }

            idx = (idx + 1) % n;
            scanned++;
        }

        ROUND_ROBIN_INDEX.set(idx);
        return slice;
    }

    public int getCycleCount() {
        return CYCLE_COUNT.get();
    }

    public int getVisitedRoutesCount() {
        return VISITED_ROUTES.size();
    }

    public int getRoundRobinIndex() {
        return ROUND_ROBIN_INDEX.get();
    }

    private List<RouteInfo> fetchAllRoutes() {
        try {
            if (shouldStopNow()) {
                return Collections.emptyList();
            }

            // ✅ 전역 일일 쿼터 체크(호출 직전)
            if (apiQuotaManager != null && !apiQuotaManager.tryConsume(1)) {
                stopCollectorDueToQuota("quotaRemainingToday=0 before getRouteNoList", null);
                return Collections.emptyList();
            }

            URI uri = UriComponentsBuilder
                .fromHttpUrl(TAGO_ROUTE_BASE_URL + OP_GET_ROUTE_NO_LIST)
                .queryParam("serviceKey", SERVICE_KEY)
                .queryParam("_type", "json")
                .queryParam("cityCode", CITY_CODE_DAEJEON)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 5000)
                .build(true)
                .toUri();

            String json = restTemplate.getForObject(uri, String.class);
            sleepApi();

            if (shouldStopNow()) {
                return Collections.emptyList();
            }

            if (isBlank(json)) {
                System.out.println("[COLLECTOR] fetchAllRoutes: empty response");
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode header = root.path("response").path("header");
            JsonNode items = root.path("response").path("body").path("items").path("item");

            String code = header.path("resultCode").asText();
            String msg  = header.path("resultMsg").asText();

            System.out.println("[COLLECTOR] routesApi resultCode=" + code + " resultMsg=" + msg);
            System.out.println("[COLLECTOR] routesApi itemsType=" + items.getNodeType());

            // ✅ resultCode != 00 인 경우(특히 quota exceeded) 더 이상 진행하지 않는다.
            if (!Objects.equals("00", code)) {
                if (isQuotaExceededMessage(msg)) {
                    stopCollectorDueToQuota("getRouteNoList resultCode=" + code + " msg=" + msg, json);
                }
                return Collections.emptyList();
            }

            Set<String> routeIdSet = new LinkedHashSet<>();

            if (items.isArray()) {
                for (JsonNode it : items) {
                    if (shouldStopNow()) break;

                    String routeId = firstNonBlank(
                        text(it, "routeid"),
                        text(it, "routeId"),
                        text(it, "route_id")
                    );
                    if (!isBlank(routeId)) {
                        routeIdSet.add(routeId);
                    }
                }
            } else if (items.isObject()) {
                String routeId = firstNonBlank(
                    text(items, "routeid"),
                    text(items, "routeId"),
                    text(items, "route_id")
                );
                if (!isBlank(routeId)) {
                    routeIdSet.add(routeId);
                }
            }

            System.out.println("[COLLECTOR] routesApi parsedRouteIds(unique)=" + routeIdSet.size());

            StringBuilder sample = new StringBuilder();
            int c = 0;
            for (String rid : routeIdSet) {
                if (c++ >= 10) break;
                if (sample.length() > 0) sample.append(", ");
                sample.append(rid);
            }
            System.out.println("[COLLECTOR] routesApi routeIdSample10=" + sample);

            List<RouteInfo> result = new ArrayList<>(routeIdSet.size());
            for (String rid : routeIdSet) {
                if (shouldStopNow()) break;
                result.add(new RouteInfo(rid));
            }

            return result;

        } catch (HttpStatusCodeException e) {
            // ✅ 429는 "오늘 쿼터 소진"이 확정된 신호이므로, 즉시 수집을 멈춘다.
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                stopCollectorDueToQuota("routesApi HTTP_429", e.getResponseBodyAsString());
            }
            System.out.println("[COLLECTOR][ERROR] fetchAllRoutes httpStatus=" + e.getStatusCode() + " msg=" + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            System.out.println("[COLLECTOR][ERROR] fetchAllRoutes msg=" + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<StopOnRoute> fetchStopsByRoute(String routeId) {
        if (isBlank(routeId)) {
            return Collections.emptyList();
        }

        if (shouldStopNow()) {
            return Collections.emptyList();
        }

        List<StopOnRoute> cached = ROUTE_STOPS_CACHE.get(routeId);
        if (cached != null) {
            return cached;
        }

        List<StopOnRoute> primary = fetchStopsByRouteInternal(routeId, "routeId");
        List<StopOnRoute> result;

        if (primary.size() >= 2) {
            result = primary;
        } else {
            List<StopOnRoute> fallback = fetchStopsByRouteInternal(routeId, "routeid");
            result = fallback;
        }

        List<StopOnRoute> toCache = (result == null) ? Collections.emptyList() : Collections.unmodifiableList(result);
        ROUTE_STOPS_CACHE.put(routeId, toCache);

        return toCache;
    }

    private List<StopOnRoute> fetchStopsByRouteInternal(String routeId, String routeIdParamKey) {
        try {
            if (shouldStopNow()) {
                return Collections.emptyList();
            }

            // ✅ 전역 일일 쿼터 체크(호출 직전)
            if (apiQuotaManager != null && !apiQuotaManager.tryConsume(1)) {
                stopCollectorDueToQuota("quotaRemainingToday=0 before getRouteAcctoThrghSttnList", null);
                return Collections.emptyList();
            }

            URI uri = UriComponentsBuilder
                .fromHttpUrl(TAGO_ROUTE_BASE_URL + OP_GET_ROUTE_STOPS)
                .queryParam("serviceKey", SERVICE_KEY)
                .queryParam("_type", "json")
                .queryParam("cityCode", CITY_CODE_DAEJEON)
                .queryParam(routeIdParamKey, routeId)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 5000)
                .build(true)
                .toUri();

            String json = restTemplate.getForObject(uri, String.class);
            sleepApi();

            if (shouldStopNow()) {
                return Collections.emptyList();
            }

            if (isBlank(json)) {
                System.out.println("[COLLECTOR] fetchStopsByRoute: empty response routeId=" + routeId + " key=" + routeIdParamKey);
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("response").path("body").path("items").path("item");

            String code = root.path("response").path("header").path("resultCode").asText();
            String msg  = root.path("response").path("header").path("resultMsg").asText();

            // ✅ resultCode != 00 이면서 quota exceeded가 확인되면 즉시 중단
            if (!Objects.equals("00", code) && isQuotaExceededMessage(msg)) {
                stopCollectorDueToQuota("stopsApi resultCode=" + code + " msg=" + msg, json);
                return Collections.emptyList();
            }
            System.out.println("[COLLECTOR] stopsApi routeId=" + routeId
                + " key=" + routeIdParamKey
                + " resultCode=" + code
                + " resultMsg=" + msg);
            System.out.println("[COLLECTOR] stopsApi routeId=" + routeId
                + " key=" + routeIdParamKey
                + " itemsType=" + items.getNodeType());

            List<StopOnRoute> result = new ArrayList<>();

            if (items.isArray()) {
                for (JsonNode it : items) {
                    if (shouldStopNow()) break;

                    StopOnRoute s = parseStopOnRoute(it);
                    if (s != null) result.add(s);
                }
            } else if (items.isObject()) {
                StopOnRoute s = parseStopOnRoute(items);
                if (s != null) result.add(s);
            }

            return result;

        } catch (HttpStatusCodeException e) {
            // ✅ 429는 "오늘 쿼터 소진" 확정 신호
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                stopCollectorDueToQuota("stopsApi HTTP_429", e.getResponseBodyAsString());
            }
            System.out.println("[COLLECTOR][ERROR] fetchStopsByRoute routeId=" + routeId + " key=" + routeIdParamKey
                + " httpStatus=" + e.getStatusCode() + " msg=" + e.getMessage());
            return Collections.emptyList();

        } catch (Exception e) {
            System.out.println("[COLLECTOR][ERROR] fetchStopsByRoute routeId=" + routeId + " key=" + routeIdParamKey + " msg=" + e.getMessage());
            return Collections.emptyList();
        }
    }

    private StopOnRoute parseStopOnRoute(JsonNode it) {
        String nodeId = firstNonBlank(
            text(it, "nodeid"),
            text(it, "nodeId"),
            text(it, "node_id"),
            text(it, "nodeNo"),
            text(it, "node_no"),
            text(it, "sttnid"),
            text(it, "sttnId"),
            text(it, "sttn_id"),
            text(it, "sttnno"),
            text(it, "sttnNo")
        );

        Double lat = firstNonNull(
            number(it, "gpslati"), number(it, "gpsLati"),
            number(it, "latitude"), number(it, "lat"),
            number(it, "gpsY"), number(it, "gps_y")
        );

        Double lng = firstNonNull(
            number(it, "gpslong"), number(it, "gpsLong"),
            number(it, "longitude"), number(it, "lon"), number(it, "lng"),
            number(it, "gpsX"), number(it, "gps_x")
        );

        Integer seq = firstNonNullInt(
            integer(it, "routeseq"), integer(it, "routeSeq"),
            integer(it, "sttnseq"), integer(it, "sttnSeq"),
            integer(it, "sttnord"), integer(it, "sttnOrd"),
            integer(it, "nodeord"), integer(it, "nodeOrd"),
            integer(it, "ord"), integer(it, "seq")
        );

        Integer updowncd = firstNonNullInt(
            integer(it, "updowncd"), integer(it, "upDownCd"),
            integer(it, "updownCd"), integer(it, "upDowncd")
        );

        if (nodeId == null || lat == null || lng == null || seq == null) {
            return null;
        }

        return new StopOnRoute(nodeId, lat, lng, seq, (updowncd == null ? 0 : updowncd.intValue()));
    }

    private Integer estimateTravelSecondsByArrivalDiffCached(
        String routeId,
        String fromNodeId,
        String toNodeId,
        Map<String, Integer> arrivalCache,
        int[] arrivalApiCalls,
        RefineDiag diag
    ) {
        if (shouldStopNow()) return null;

        if (diag != null && diag.budgetExhausted()) {
            diag.limitHit++;
            return null;
        }

        if (isBlank(routeId)) return null;
        if (isBlank(fromNodeId)) return null;
        if (isBlank(toNodeId)) return null;

        Integer fromArr = fetchArrivalSecondsCached(routeId, fromNodeId, arrivalCache, arrivalApiCalls, diag);
        Integer toArr   = fetchArrivalSecondsCached(routeId, toNodeId, arrivalCache, arrivalApiCalls, diag);

        if (fromArr == null || toArr == null) return null;

        int diff = toArr - fromArr;
        if (diff <= 0) {
            if (diag != null) diag.diffNonPositive++;
            return null;
        }

        return diff;
    }

    private Integer fetchArrivalSecondsCached(
        String routeId,
        String nodeId,
        Map<String, Integer> arrivalCache,
        int[] arrivalApiCalls,
        RefineDiag diag
    ) {
        if (shouldStopNow()) return null;

        if (diag != null && diag.budgetExhausted()) {
            diag.limitHit++;
            return null;
        }

        if (isBlank(nodeId)) return null;

        if (arrivalCache.containsKey(nodeId)) {
            return arrivalCache.get(nodeId);
        }

        // ✅ per-route 제한(트래픽 최소화)
        if (arrivalApiCalls != null && arrivalApiCalls.length > 0) {
            if (arrivalApiCalls[0] >= ARRIVAL_API_LIMIT_PER_ROUTE) {
                if (diag != null) diag.limitHit++;
                arrivalCache.put(nodeId, null);
                return null;
            }
            // ✅ 전역 일일 쿼터 체크(호출 직전)
            //    - 호출을 "막는" 것이지 호출 수를 늘리지 않는다.
            if (apiQuotaManager != null && !apiQuotaManager.tryConsume(1)) {
                if (diag != null) diag.limitHit++;
                arrivalCache.put(nodeId, null);
                return null;
            }

            arrivalApiCalls[0]++;
        }

        // ✅ callsBudget 예산(Refine에서만 의미)
        if (diag != null) {
            if (diag.budgetExhausted()) {
                diag.limitHit++;
                arrivalCache.put(nodeId, null);
                return null;
            }
            diag.callsUsed++;
        }

        Integer v = fetchArrivalSeconds(routeId, nodeId, diag);
        arrivalCache.put(nodeId, v);
        return v;
    }

    /**
     * ✅ arrival API 호출 1회
     * - resultCode != "00" -> apiError
     * - items/item 경로가 없거나 비정상 타입 -> arrivalItemsEmpty
     * - routeId 매칭 실패 -> routeIdMatchFail
     * - routeId 매칭 성공 but arrtime missing/0 -> arrtimeMissing
     * - arrtime > 0 -> arrivalOk
     *
     * ※ 원문(JSON/텍스트) 1개만 있으면 원인 확정이 가능하므로,
     *   최초 1회 raw를 diag에 저장한다.
     */
    private Integer fetchArrivalSeconds(String routeId, String nodeId, RefineDiag diag) {
        try {
            if (shouldStopNow()) return null;
            if (diag != null && diag.budgetExhausted()) {
                diag.limitHit++;
                return null;
            }

            URI uri = UriComponentsBuilder
                .fromHttpUrl(TAGO_ARRIVAL_BASE_URL + OP_GET_ARRIVAL_BY_STTN)
                .queryParam("serviceKey", SERVICE_KEY)
                .queryParam("_type", "json")
                .queryParam("cityCode", CITY_CODE_DAEJEON)
                .queryParam("nodeId", nodeId)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 300)
                .build(true)
                .toUri();

            String json = restTemplate.getForObject(uri, String.class);
            sleepApi();

            if (shouldStopNow()) return null;

            if (isBlank(json)) {
                if (diag != null) {
                    // 빈 응답은 "구조/빈 결과"로 보고 arrivalItemsEmpty로 분리(진짜 에러와 구분)
                    diag.arrivalItemsEmpty++;
                    diag.recordRawOnce("", null, "empty_response");
                }
                return null;
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode header = root.path("response").path("header");
            String code = header.path("resultCode").asText();
            String msg  = header.path("resultMsg").asText();

            if (!Objects.equals("00", code)) {
                if (diag != null) {
                    diag.apiError++;
                    diag.recordRawOnce(json, false, "resultCode=" + code + " msg=" + msg);
                }

                // ✅ quota exceeded가 확정되면 즉시 수집을 멈춘다(남은 루프에서 반복 호출 금지)
                if (isQuotaExceededMessage(msg)) {
                    stopCollectorDueToQuota("arrivalApi resultCode=" + code + " msg=" + msg, json);
                }
                return null;
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");

            // ✅ items가 아예 없거나 타입 불일치면: 파싱 경로/응답 구조/빈 결과 케이스로 분리
            if (!(items.isArray() || items.isObject())) {
                if (diag != null) {
                    diag.arrivalItemsEmpty++;
                    diag.recordRawOnce(json, true, "itemsType=" + items.getNodeType());
                }
                return null;
            }

            boolean anyMatchedRoute = false;

            if (items.isArray()) {
                for (JsonNode it : items) {
                    if (shouldStopNow()) return null;

                    String rid = firstNonBlank(text(it, "routeid"), text(it, "routeId"));
                    if (!Objects.equals(routeId, rid)) {
                        continue;
                    }

                    anyMatchedRoute = true;

                    Integer arr = firstNonNullInt(integer(it, "arrtime"), integer(it, "arrTime"));
                    if (arr != null && arr > 0) {
                        if (diag != null) {
                            diag.arrivalOk++;
                            diag.recordRawOnce(json, true, "ok(arrtime>0)");
                        }
                        return arr;
                    } else {
                        if (diag != null) {
                            diag.arrtimeMissing++;
                            diag.recordRawOnce(json, true, "matched_but_arrtime_missing");
                        }
                        return null;
                    }
                }
            } else {
                String rid = firstNonBlank(text(items, "routeid"), text(items, "routeId"));
                if (Objects.equals(routeId, rid)) {
                    anyMatchedRoute = true;
                    Integer arr = firstNonNullInt(integer(items, "arrtime"), integer(items, "arrTime"));
                    if (arr != null && arr > 0) {
                        if (diag != null) {
                            diag.arrivalOk++;
                            diag.recordRawOnce(json, true, "ok(arrtime>0)");
                        }
                        return arr;
                    } else {
                        if (diag != null) {
                            diag.arrtimeMissing++;
                            diag.recordRawOnce(json, true, "matched_but_arrtime_missing");
                        }
                        return null;
                    }
                }
            }

            // ✅ items는 있는데 routeId 매칭 항목이 없으면: routeIdMatchFail
            if (!anyMatchedRoute && diag != null) {
                diag.routeIdMatchFail++;
                diag.recordRawOnce(json, true, "no_route_match");
            }

            return null;

        } catch (HttpStatusCodeException e) {
            // ✅ 429는 "오늘 쿼터 소진" 확정 신호
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                stopCollectorDueToQuota("arrivalApi HTTP_429", e.getResponseBodyAsString());
            }
            if (diag != null) {
                diag.apiError++;
                diag.recordRawOnce(e.getResponseBodyAsString(), null, "httpStatus=" + e.getStatusCode());
            }
            return null;

        } catch (Exception e) {
            if (diag != null) {
                diag.apiError++;
                diag.recordRawOnce(null, null, "exception=" + e.getMessage());
            }
            return null;
        }
    }

    private Integer estimateTravelSecondsByDistanceFallback(double distanceM) {
        if (distanceM <= 0) return null;

        double speedMps = (BUS_FALLBACK_SPEED_KMH * 1000.0) / 3600.0;
        if (speedMps <= 0) return null;

        int sec = (int) Math.round(distanceM / speedMps);

        if (sec < MIN_TRAVEL_SEC) sec = MIN_TRAVEL_SEC;
        if (sec > MAX_TRAVEL_SEC) sec = MAX_TRAVEL_SEC;

        return sec;
    }

    private boolean upsertSegmentWeightBus(
        String routeId,
        int updowncd,
        StopOnRoute from,
        StopOnRoute to,
        double distanceM,
        int travelSecSample
    ) {

        if (shouldStopNow()) {
            return false;
        }

        String sql =
            "INSERT INTO segment_weight (" +
            "  mode, route_id, updowncd, from_id, to_id, " +
            "  from_lat, from_lng, to_lat, to_lng, " +
            "  distance_m, travel_sec_avg, sample_count, updated_at" +
            ") VALUES (" +
            "  'BUS', ?, ?, ?, ?, " +
            "  ?, ?, ?, ?, " +
            "  ?, ?, 1, NOW()" +
            ") " +
            "ON DUPLICATE KEY UPDATE " +
            "  travel_sec_avg = (travel_sec_avg * sample_count + VALUES(travel_sec_avg)) / (sample_count + 1), " +
            "  sample_count = sample_count + 1, " +
            "  distance_m = VALUES(distance_m), " +
            "  from_lat = VALUES(from_lat), " +
            "  from_lng = VALUES(from_lng), " +
            "  to_lat = VALUES(to_lat), " +
            "  to_lng = VALUES(to_lng), " +
            "  updated_at = NOW()";

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(true);

            ps.setString(1, routeId);
            ps.setInt(2, updowncd);
            ps.setString(3, from.nodeId);
            ps.setString(4, to.nodeId);

            ps.setDouble(5, from.lat);
            ps.setDouble(6, from.lng);
            ps.setDouble(7, to.lat);
            ps.setDouble(8, to.lng);

            ps.setDouble(9, distanceM);
            ps.setDouble(10, travelSecSample);

            int affected = ps.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            String msg = e.getMessage();
            String sqlState = e.getSQLState();

            System.out.println(
                "[COLLECTOR][DB ERROR] routeId=" + routeId +
                " updowncd=" + updowncd +
                " from=" + safeNode(from) +
                " to=" + safeNode(to) +
                " sqlState=" + sqlState +
                " vendorCode=" + e.getErrorCode() +
                " msg=" + msg
            );

            // ✅ 핵심: "has been closed" 단일 문자열에 의존하면 DB/드라이버/풀 구현체에 따라 놓친다.
            // - SQLState 08xxx: Connection Exception 계열
            // - msg 다중 패턴: 풀/커넥션 종료/통신 장애 케이스 포함
            boolean connectionExceptionState = (sqlState != null && sqlState.startsWith("08"));

            boolean poolClosedMsg =
                msg != null && (
                    msg.contains("has been closed") ||
                    msg.contains("HikariDataSource") ||
                    msg.contains("HikariPool") ||
                    (msg.contains("Pool") && msg.contains("closed")) ||
                    msg.contains("Connection is closed") ||
                    msg.contains("connection is closed") ||
                    msg.contains("Communications link failure")
                );

            if (connectionExceptionState || poolClosedMsg) {
                DB_CLOSED_FATAL.set(true);
                Thread.currentThread().interrupt();
            }

            return false;
        }
    }

    private static String safeNode(StopOnRoute s) {
        if (s == null) return "null";
        return s.nodeId;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asText();
    }

    private static Double number(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.asDouble();
        String s = v.asText();
        if (isBlank(s)) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isInt() || v.isLong() || v.isNumber()) return v.asInt();
        String s = v.asText();
        if (isBlank(s)) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return null;
    }

    private static Double firstNonNull(Double... values) {
        if (values == null) return null;
        for (Double v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static Integer firstNonNullInt(Integer... values) {
        if (values == null) return null;
        for (Integer v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static void sleepApi() {
        try {
            Thread.sleep(API_SLEEP_MS);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    private static class RouteInfo {
        private final String routeId;
        private RouteInfo(String routeId) { this.routeId = routeId; }
    }

    private static class StopOnRoute {
        private final String nodeId;
        private final double lat;
        private final double lng;
        private final int routeSeq;
        private final int updowncd;

        private StopOnRoute(String nodeId, double lat, double lng, int routeSeq, int updowncd) {
            this.nodeId = nodeId;
            this.lat = lat;
            this.lng = lng;
            this.routeSeq = routeSeq;
            this.updowncd = updowncd;
        }
    }
}

// 수정됨 끝
