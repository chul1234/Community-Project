// 수정됨: route-stops(노선별 경유정류장 목록) API를 노선(routeId)당 "최초 1회만" 호출하도록 메모리 캐시(ROUTE_STOPS_CACHE) 적용
//        - Collector 주기 실행 시 동일 routeId에 대해 route-stops를 반복 호출하지 않음(트래픽 폭발 방지)
//        - 실패/빈 결과도 캐시에 저장하여 동일 노선에 대한 재시도 호출을 막음(서버 재기동 전까지)

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
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
    private static final boolean SEED_DISTANCE_ONLY = true;

    // ✅ 2차(Refine) 모드에서 arrival API 호출을 너무 많이 하지 않도록 노선당 호출 상한
    //    - 상한을 넘으면 해당 노선은 남은 구간을 거리 기반 fallback으로 처리한다(멈춤 방지)
    private static final int ARRIVAL_API_LIMIT_PER_ROUTE = 80;

    // =========================
    // TAGO 기본 설정
    // =========================
    private static final String CITY_CODE_DAEJEON = "25";

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
    private static final Set<String> VISITED_ROUTES = ConcurrentHashMap.newKeySet();

    // =========================
    // route-stops 캐시(핵심)
    // =========================
    // routeId -> 정류장 목록(서버 생명주기 동안 1회 호출 후 재사용)
    // - 빈/실패 결과도 캐시에 저장하여 같은 노선에 대한 재호출을 방지한다.
    private static final Map<String, List<StopOnRoute>> ROUTE_STOPS_CACHE = new ConcurrentHashMap<>();

    @Autowired
    private DataSource dataSource;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void collectOnce() {
        collectOnce(5);
    }

    public void collectOnce(int batchSize) {
        if (batchSize <= 0) {
            System.out.println("[COLLECTOR] batchSize must be > 0");
            return;
        }

        // 1) 전체 노선 목록 조회
        List<RouteInfo> routes = fetchAllRoutes();
        if (routes.isEmpty()) {
            System.out.println("[COLLECTOR] route list is empty. stop.");
            return;
        }

        // 2) 사이클 완료 시 방문 집합 초기화(다음 사이클 준비)
        if (VISITED_ROUTES.size() >= routes.size()) {
            System.out.println("[COLLECTOR] cycle complete (visited=" + VISITED_ROUTES.size() + "/" + routes.size() + "). reset visited.");
            VISITED_ROUTES.clear();
        }

        // 3) 이번 호출에서 처리할 노선 선택(라운드로빈)
        List<RouteInfo> targetRoutes = selectRoundRobinSlice(routes, batchSize);

        // 4) 이번에 실제 처리 대상 routeId 출력(눈으로 순환 확인)
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

        int totalSegmentsUpserted = 0;
        int totalSegmentsSkippedNoTime = 0;
        int totalSegmentsTried = 0;

        // 5) 노선별 처리
        for (RouteInfo route : targetRoutes) {
            long routeStartMs = System.currentTimeMillis();
            System.out.println("[COLLECTOR] routeStart=" + route.routeId);

            // ✅ 2차(Refine) 때만 쓰는 arrival 캐시(노선 1개 처리 동안 nodeId 중복 호출 제거)
            Map<String, Integer> arrivalCache = new HashMap<>();
            int[] arrivalApiCalls = new int[] { 0 };

            try {
                // 5-1) 노선별 정류장 목록 조회 (✅ 캐시 적용: routeId당 최초 1회만 API 호출)
                List<StopOnRoute> stops = fetchStopsByRoute(route.routeId);
                if (stops.size() < 2) {
                    System.out.println("[COLLECTOR] routeId=" + route.routeId + " stops < 2. skip.");
                    long elapsedMs = System.currentTimeMillis() - routeStartMs;
                    System.out.println("[COLLECTOR] routeEnd=" + route.routeId + " elapsedMs=" + elapsedMs + " (skip)");
                    continue;
                }

                // 5-2) updowncd(상/하행)별로 그룹핑 후, 각 그룹 내 routeSeq 순으로 정렬하여 세그먼트 생성
                Map<Integer, List<StopOnRoute>> stopsByDir = new HashMap<>();
                for (StopOnRoute s : stops) {
                    // updowncd가 누락된 경우(드물게 발생 가능)는 0(상행)으로 처리한다.
                    int dir = (s == null) ? 0 : s.updowncd;
                    stopsByDir.computeIfAbsent(dir, k -> new ArrayList<>()).add(s);
                }

                int upsertedForRoute = 0;
                int skippedForRouteNoTime = 0;
                int triedForRoute = 0;

                int arrivalUsed = 0;
                int fallbackUsed = 0;

                // 5-3) 방향별로 인접 정류장 쌍을 세그먼트로 처리
                //      - dir=0:상행, dir=1:하행
                for (Map.Entry<Integer, List<StopOnRoute>> e : stopsByDir.entrySet()) {
                    Integer dir = e.getKey();
                    List<StopOnRoute> dirStops = e.getValue();

                    if (dirStops == null || dirStops.size() < 2) {
                        continue;
                    }

                    // 방향 그룹 내부는 routeSeq 순으로 정렬
                    dirStops.sort(Comparator.comparingInt(s -> s.routeSeq));

                    for (int i = 0; i < dirStops.size() - 1; i++) {
                        StopOnRoute from = dirStops.get(i);
                        StopOnRoute to   = dirStops.get(i + 1);

                        // 거리(m) 계산
                        double distanceM = haversineMeters(from.lat, from.lng, to.lat, to.lng);

                        Integer travelSecSample;

                        // ✅ 1차(Seed) 모드면 arrival API를 절대 호출하지 않는다(즉시 전체 순환/적재 목적)
                        if (SEED_DISTANCE_ONLY) {
                            travelSecSample = estimateTravelSecondsByDistanceFallback(distanceM);
                            fallbackUsed++;
                        } else {
                            // ✅ 2차(Refine): arrival 기반(캐시+상한) → 실패 시 거리 기반 fallback
                            travelSecSample = estimateTravelSecondsByArrivalDiffCached(
                                route.routeId,
                                from.nodeId,
                                to.nodeId,
                                arrivalCache,
                                arrivalApiCalls
                            );

                            if (travelSecSample != null && travelSecSample > 0) {
                                arrivalUsed++;
                            }

                            if (travelSecSample == null || travelSecSample <= 0) {
                                travelSecSample = estimateTravelSecondsByDistanceFallback(distanceM);
                                fallbackUsed++;
                            }
                        }

                        // 시간이 끝까지 안 나오면 skip
                        if (travelSecSample == null || travelSecSample <= 0) {
                            skippedForRouteNoTime++;
                            continue;
                        }

                        // 5-4) DB UPSERT(세그먼트 가중치 누적) - updowncd(상/하행) 포함
                        boolean ok = upsertSegmentWeightBus(
                            route.routeId,
                            dir != null ? dir.intValue() : 0,
                            from,
                            to,
                            distanceM,
                            travelSecSample
                        );

                        triedForRoute++;
                        if (ok) {
                            upsertedForRoute++;
                        }
                    }
                }

                totalSegmentsUpserted += upsertedForRoute;
                totalSegmentsSkippedNoTime += skippedForRouteNoTime;
                totalSegmentsTried += triedForRoute;

                System.out.println("[COLLECTOR] routeId=" + route.routeId
                    + " tried=" + triedForRoute
                    + " upserted=" + upsertedForRoute
                    + " skippedNoTime=" + skippedForRouteNoTime
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
        }

        // 6) 사이클 완료 시점 안내
        if (VISITED_ROUTES.size() >= routes.size()) {
            System.out.println("[COLLECTOR] ALL ROUTES VISITED (visited=" + VISITED_ROUTES.size() + "/" + routes.size() + "). next call will start new cycle.");
        }

        System.out.println("[COLLECTOR] done. routes=" + targetRoutes.size()
            + ", segmentsTried=" + totalSegmentsTried
            + ", totalSegmentsUpserted=" + totalSegmentsUpserted
            + ", totalSkippedNoTime=" + totalSegmentsSkippedNoTime);
    }

    /**
     * 라운드로빈 슬라이스:
     * - ROUND_ROBIN_INDEX부터 한 바퀴까지 스캔하면서
     * - 이번 사이클에서 아직 처리 안 한 routeId만 batchSize개 모은다.
     * - 다음 호출 시작 인덱스로 ROUND_ROBIN_INDEX를 갱신한다.
     */
    private List<RouteInfo> selectRoundRobinSlice(List<RouteInfo> routes, int batchSize) {
        int n = routes.size();
        int start = ROUND_ROBIN_INDEX.get();

        List<RouteInfo> slice = new ArrayList<>(Math.min(batchSize, n));

        int idx = start;
        int scanned = 0;

        while (scanned < n && slice.size() < batchSize) {
            RouteInfo r = routes.get(idx);

            if (r != null && r.routeId != null && !r.routeId.isBlank()) {
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

    // =========================================================
    // 1) 전체 노선 목록 조회
    // =========================================================
    private List<RouteInfo> fetchAllRoutes() {
        try {
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

            if (json == null || json.isBlank()) {
                System.out.println("[COLLECTOR] fetchAllRoutes: empty response");
                return List.of();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode header = root.path("response").path("header");
            JsonNode items = root.path("response").path("body").path("items").path("item");

            String code = header.path("resultCode").asText();
            String msg  = header.path("resultMsg").asText();

            System.out.println("[COLLECTOR] routesApi resultCode=" + code + " resultMsg=" + msg);
            System.out.println("[COLLECTOR] routesApi itemsType=" + items.getNodeType());

            // routeId 중복 제거 + 순서 유지
            Set<String> routeIdSet = new LinkedHashSet<>();

            if (items.isArray()) {
                for (JsonNode it : items) {
                    String routeId = firstNonBlank(
                        text(it, "routeid"),
                        text(it, "routeId"),
                        text(it, "route_id")
                    );
                    if (routeId != null && !routeId.isBlank()) {
                        routeIdSet.add(routeId);
                    }
                }
            } else if (items.isObject()) {
                String routeId = firstNonBlank(
                    text(items, "routeid"),
                    text(items, "routeId"),
                    text(items, "route_id")
                );
                if (routeId != null && !routeId.isBlank()) {
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
                result.add(new RouteInfo(rid));
            }

            return result;

        } catch (Exception e) {
            System.out.println("[COLLECTOR][ERROR] fetchAllRoutes msg=" + e.getMessage());
            return List.of();
        }
    }

    // =========================================================
    // 2) 노선별 정류장 목록 조회 (✅ routeId당 최초 1회만 API 호출하도록 캐시 적용)
    // =========================================================
    private List<StopOnRoute> fetchStopsByRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return List.of();
        }

        // ✅ 1) 캐시에 있으면 즉시 반환(트래픽 0)
        List<StopOnRoute> cached = ROUTE_STOPS_CACHE.get(routeId);
        if (cached != null) {
            return cached;
        }

        // ✅ 2) 캐시에 없을 때만 실제 TAGO route-stops 호출(최초 1회)
        List<StopOnRoute> primary = fetchStopsByRouteInternal(routeId, "routeId");
        List<StopOnRoute> result;

        if (primary.size() >= 2) {
            result = primary;
        } else {
            List<StopOnRoute> fallback = fetchStopsByRouteInternal(routeId, "routeid");
            result = fallback;
        }

        // ✅ 3) 실패/빈 결과도 캐시에 저장하여 동일 노선 재호출을 막는다.
        //      (route-stops는 정적 데이터 성격이라 서버 생명주기 동안 재시도 필요성이 낮다)
        List<StopOnRoute> toCache = (result == null) ? List.of() : Collections.unmodifiableList(result);
        ROUTE_STOPS_CACHE.put(routeId, toCache);

        return toCache;
    }

    private List<StopOnRoute> fetchStopsByRouteInternal(String routeId, String routeIdParamKey) {
        try {
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

            if (json == null || json.isBlank()) {
                System.out.println("[COLLECTOR] fetchStopsByRoute: empty response routeId=" + routeId + " key=" + routeIdParamKey);
                return List.of();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("response").path("body").path("items").path("item");

            String code = root.path("response").path("header").path("resultCode").asText();
            String msg  = root.path("response").path("header").path("resultMsg").asText();
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
                    StopOnRoute s = parseStopOnRoute(it);
                    if (s != null) result.add(s);
                }
            } else if (items.isObject()) {
                StopOnRoute s = parseStopOnRoute(items);
                if (s != null) result.add(s);
            }

            return result;

        } catch (Exception e) {
            System.out.println("[COLLECTOR][ERROR] fetchStopsByRoute routeId=" + routeId + " key=" + routeIdParamKey + " msg=" + e.getMessage());
            return List.of();
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

    // =========================================================
    // 3) arrival 기반 시간(2차 Refine) - 캐시 + 호출 상한
    // =========================================================
    private Integer estimateTravelSecondsByArrivalDiffCached(
        String routeId,
        String fromNodeId,
        String toNodeId,
        Map<String, Integer> arrivalCache,
        int[] arrivalApiCalls
    ) {
        if (routeId == null || routeId.isBlank()) return null;
        if (fromNodeId == null || fromNodeId.isBlank()) return null;
        if (toNodeId == null || toNodeId.isBlank()) return null;

        Integer fromArr = fetchArrivalSecondsCached(routeId, fromNodeId, arrivalCache, arrivalApiCalls);
        Integer toArr   = fetchArrivalSecondsCached(routeId, toNodeId, arrivalCache, arrivalApiCalls);

        if (fromArr == null || toArr == null) return null;

        int diff = toArr - fromArr;
        if (diff <= 0) return null;

        return diff;
    }

    private Integer fetchArrivalSecondsCached(
        String routeId,
        String nodeId,
        Map<String, Integer> arrivalCache,
        int[] arrivalApiCalls
    ) {
        if (nodeId == null || nodeId.isBlank()) return null;

        if (arrivalCache.containsKey(nodeId)) {
            return arrivalCache.get(nodeId);
        }

        if (arrivalApiCalls != null && arrivalApiCalls.length > 0) {
            if (arrivalApiCalls[0] >= ARRIVAL_API_LIMIT_PER_ROUTE) {
                arrivalCache.put(nodeId, null);
                return null;
            }
            arrivalApiCalls[0]++;
        }

        Integer v = fetchArrivalSeconds(routeId, nodeId);
        arrivalCache.put(nodeId, v);
        return v;
    }

    private Integer fetchArrivalSeconds(String routeId, String nodeId) {
        try {
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

            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode it : items) {
                    String rid = firstNonBlank(text(it, "routeid"), text(it, "routeId"));
                    if (!Objects.equals(routeId, rid)) continue;

                    Integer arr = firstNonNullInt(integer(it, "arrtime"), integer(it, "arrTime"));
                    if (arr != null && arr > 0) return arr;
                }
            }

            if (items.isObject()) {
                String rid = firstNonBlank(text(items, "routeid"), text(items, "routeId"));
                if (!Objects.equals(routeId, rid)) return null;

                Integer arr = firstNonNullInt(integer(items, "arrtime"), integer(items, "arrTime"));
                if (arr != null && arr > 0) return arr;
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    // 4) 거리 기반 fallback 시간(1차 Seed 핵심)
    // =========================================================
    private Integer estimateTravelSecondsByDistanceFallback(double distanceM) {
        if (distanceM <= 0) return null;

        double speedMps = (BUS_FALLBACK_SPEED_KMH * 1000.0) / 3600.0;
        if (speedMps <= 0) return null;

        int sec = (int) Math.round(distanceM / speedMps);

        if (sec < MIN_TRAVEL_SEC) sec = MIN_TRAVEL_SEC;
        if (sec > MAX_TRAVEL_SEC) sec = MAX_TRAVEL_SEC;

        return sec;
    }

    // =========================================================
    // 5) DB UPSERT
    // =========================================================
    private boolean upsertSegmentWeightBus(
        String routeId,
        int updowncd,
        StopOnRoute from,
        StopOnRoute to,
        double distanceM,
        int travelSecSample
    ) {

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
            System.out.println(
                "[COLLECTOR][DB ERROR] routeId=" + routeId +
                " updowncd=" + updowncd +
                " from=" + safeNode(from) +
                " to=" + safeNode(to) +
                " sqlState=" + e.getSQLState() +
                " vendorCode=" + e.getErrorCode() +
                " msg=" + e.getMessage()
            );
            return false;
        }
    }

    private static String safeNode(StopOnRoute s) {
        if (s == null) return "null";
        return s.nodeId;
    }

    // =========================================================
    // 6) 거리 계산
    // =========================================================
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

    // =========================================================
    // 7) 공통 유틸
    // =========================================================
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
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isInt() || v.isLong() || v.isNumber()) return v.asInt();
        String s = v.asText();
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
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
        try { Thread.sleep(API_SLEEP_MS); } catch (InterruptedException ignore) { }
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
