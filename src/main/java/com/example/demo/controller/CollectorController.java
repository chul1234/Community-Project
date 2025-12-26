// 수정됨: /collector/status 응답에 순환(cycleCount) 관련 필드 추가 + 운행시간 23:30 포함 처리 + (메서드 유무 불명) 리플렉션으로 안전 조회

package com.example.demo.controller;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.collector.BusSegmentCollector;
import com.example.demo.collector.CollectorSwitch;
import com.example.demo.collector.ApiQuotaManager;

@RestController
public class CollectorController {

    @Autowired
    private BusSegmentCollector busSegmentCollector;

    @Autowired
    private ApiQuotaManager apiQuotaManager;
@Autowired
    private CollectorSwitch collectorSwitch;

    // =========================
    // 자동 수집(서버 백그라운드) 상태
    // =========================
    // 주의: 실제 ON/OFF는 CollectorSwitch가 기준이다.
    //       (프론트는 "running" 필드를 보므로 status에선 switch 상태를 그대로 내려준다.)

    // interval(다음 실행까지 쉬는 시간)
    private final AtomicInteger intervalMs = new AtomicInteger(60000);

    // ✅ batchSize는 서버가 자동으로 조절
    private final AtomicInteger batchSize = new AtomicInteger(6);

    // ✅ arrtime(도착예정시간) 샘플 수집 상한(1회 루프당 호출 수)
    private final AtomicInteger refineCallsPerLoop = new AtomicInteger(6);

    // 마지막 실행 시간(ms) - 배치 자동 조절 판단 기준
    private final AtomicInteger lastElapsedMs = new AtomicInteger(0);

    // 스케줄 취소 핸들
    private volatile ScheduledFuture<?> future;

    // 수집 실행이 겹치지 않게 보호(혹시라도 호출이 중첩되면 스킵)
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    // =========================
    // 운행 시간창 (04:00 ~ 23:30)
    // =========================
    private static final LocalTime WINDOW_START = LocalTime.of(4, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(23, 30);

    // 단일 스레드 스케줄러(절대 동시 실행 안 되게)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "collector-auto-runner");
            t.setDaemon(true);
            return t;
        }
    });

    // =========================
    // 기존 1회 실행(유지)
    // =========================
    @GetMapping(value = "/collector/runOnce", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runOnce(@RequestParam(defaultValue = "5") int batch) {

        long st = System.currentTimeMillis();
        busSegmentCollector.collectOnce(batch);
        int elapsed = (int) (System.currentTimeMillis() - st);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("message", "collectOnce done");
        res.put("batchSize", batch);
        res.put("elapsedMs", elapsed);
        res.put("ts", LocalDateTime.now().toString());
        return res;
    }


    // =========================
    // Refine 1회 실행 (arrtime diff 기반으로 travel_sec_avg 누적)
    // =========================
    @GetMapping(value = "/collector/runRefineOnce", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runRefineOnce(@RequestParam(defaultValue = "6") int calls) {

        long st = System.currentTimeMillis();
        invokeRefineOnceSafely(calls);
        int elapsed = (int) (System.currentTimeMillis() - st);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("mode", "REFINE");
        res.put("arrivalCallsBudget", calls);
        res.put("elapsedMs", elapsed);
        return res;
    }

    // =========================
    // ✅ 토글: "데이터 수집" 버튼용
    // =========================
    @GetMapping(value = "/collector/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> toggle() {
        boolean enabledNow = collectorSwitch.toggle();

        if (enabledNow) {
            startAutoLoop();
            return statusWithMessage("collector auto started");
        }

        stopAutoLoop();
        return statusWithMessage("collector auto stopped");
    }

    @GetMapping(value = "/collector/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> status() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("running", collectorSwitch.isEnabled());
        res.put("batchSize", batchSize.get());
        res.put("intervalMs", intervalMs.get());
        if (apiQuotaManager != null) {
            res.put("quotaUsedToday", apiQuotaManager.getUsedToday());
            res.put("quotaRemainingToday", apiQuotaManager.getRemainingToday());
        }
        res.put("lastElapsedMs", lastElapsedMs.get());
        res.put("inProgress", inProgress.get());

        // ✅ 순환/라운드로빈 상태(존재하면 내려주고, 없으면 null)
        // - BusSegmentCollector에 getCycleCount/getVisitedRoutesCount/getRoundRobinIndex 가 없을 수도 있으므로
        //   컴파일/런타임 안정성을 위해 리플렉션으로 안전 조회한다.
        res.put("cycleCount", safeInvokeInt(busSegmentCollector, "getCycleCount"));
        res.put("visitedRoutesNow", safeInvokeInt(busSegmentCollector, "getVisitedRoutesCount"));
        res.put("rrIndexNow", safeInvokeInt(busSegmentCollector, "getRoundRobinIndex"));

        boolean withinWindow = isWithinOperatingWindow();
        res.put("withinWindow", withinWindow);
        res.put("windowStart", WINDOW_START.toString());
        res.put("windowEnd", WINDOW_END.toString());

        res.put("ts", LocalDateTime.now().toString());
        return res;
    }

    // =========================
    // 내부: 자동 루프 시작/정지
    // =========================
    private synchronized void startAutoLoop() {

        // 이미 future가 살아있으면(예: 토글 연타) 추가 생성 금지
        if (future != null && !future.isCancelled() && !future.isDone()) {
            return;
        }

        // "루프"는 1개만 존재: 0ms에 시작해서 내부에서 sleep으로 interval 반영
        future = scheduler.schedule(() -> {

            while (collectorSwitch.isEnabled()) {

                // 운행 시간창 밖이면 수집은 하지 않고 주기적으로만 체크
                if (!isWithinOperatingWindow()) {
                    sleepQuietly(intervalMs.get());
                    continue;
                }

                // 겹침 방지(혹시라도 중첩되면 스킵)
                if (!inProgress.compareAndSet(false, true)) {
                    sleepQuietly(intervalMs.get());
                    continue;
                }

                long st = System.currentTimeMillis();
                int usedBatch = batchSize.get();

                try {
                    busSegmentCollector.collectOnce(usedBatch);
                    // ✅ 배치 루프마다 arrtime 기반 샘플을 함께 누적하여 travel_sec_avg를 실제값에 가깝게 보정한다.
                    busSegmentCollector.refineOnce(refineCallsPerLoop.get());
                } catch (Exception e) {
                    System.out.println("[COLLECTOR][AUTO ERROR] " + e.getMessage());
                } finally {
                    int elapsed = (int) (System.currentTimeMillis() - st);
                    lastElapsedMs.set(elapsed);

                    // ✅ 배치 자동 조절(적응형)
                    autoTuneBatch(elapsed);

                    inProgress.set(false);
                }

                // ✅ intervalMs는 매 루프마다 읽어서 즉시 반영
                sleepQuietly(intervalMs.get());
            }

        }, 0, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopAutoLoop() {
        if (future != null) {
            try { future.cancel(false); } catch (Exception ignore) {}
            future = null;
        }
    }

    @PostConstruct
    public void autoStartOnBoot() {
        // 서버가 켜져 있으면 웹페이지 없이도 자동 수집되도록 기본 ON
        // (사용자가 /collector/toggle로 언제든 OFF 가능)
        if (collectorSwitch.isEnabled()) {
            startAutoLoop();
        }
    }

    private boolean isWithinOperatingWindow() {
        LocalTime now = LocalTime.now();
        // ✅ 23:30 "포함" 처리: now가 END보다 "이후"가 아니면 허용
        return !now.isBefore(WINDOW_START) && !now.isAfter(WINDOW_END);
    }

    private void autoTuneBatch(int elapsedMs) {
        int b = batchSize.get();

        // 기준:
        // - 15초 이상: 너무 느림 -> batch 줄이기
        // - 4초 이하: 너무 빠름 -> batch 늘리기
        if (elapsedMs >= 15000) {
            b = b - 1;
        } else if (elapsedMs <= 4000) {
            b = b + 1;
        }

        if (b < 3) b = 3;
        if (b > 10) b = 10;

        batchSize.set(b);
    }

    private void sleepQuietly(int ms) {
        if (ms <= 0) ms = 1;
        try { Thread.sleep(ms); } catch (InterruptedException ignore) { }
    }

    private Map<String, Object> statusWithMessage(String msg) {
        Map<String, Object> res = status();
        res.put("message", msg);
        return res;
    }

    // =========================
    // ✅ 리플렉션 기반 안전 int 조회 (없으면 null)
    // =========================
    private Integer safeInvokeInt(Object target, String methodName) {
        if (target == null || methodName == null || methodName.trim().isEmpty()) {
            return null;
        }

        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);

            if (v == null) return null;
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Number) return ((Number) v).intValue();

            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    
    /**
     * BusSegmentCollector에 refineOnce(int)가 없을 수도 있으므로(버전 차이/브랜치 차이),
     * 리플렉션으로 존재 여부를 확인한 뒤 안전하게 호출한다.
     * - 존재하지 않으면 아무 작업도 하지 않는다.
     * - 존재하지만 호출 중 예외가 나면 로그만 남기고 루프는 계속 진행한다.
     */
    private void invokeRefineOnceSafely(int calls) {
        try {
            Method m = busSegmentCollector.getClass().getMethod("refineOnce", int.class);
            m.invoke(busSegmentCollector, calls);
        } catch (NoSuchMethodException e) {
            // refineOnce 미구현 버전: 무시
        } catch (Exception e) {
            System.out.println("[COLLECTOR][ERROR] refineOnce invoke msg=" + e.getMessage());
        }
    }

@PreDestroy
    public void shutdown() {
        try {
            stopAutoLoop();
        } catch (Exception ignore) {}

        try {
            scheduler.shutdownNow();
        } catch (Exception ignore) {}
    }
}

// 수정됨 끝
