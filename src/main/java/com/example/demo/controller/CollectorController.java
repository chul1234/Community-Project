// 수정됨: (1) runRefineOnce/autoLoop의 calls를 "arrival API 호출 예산(callsBudget)" 의미로 명확화
//        (2) quotaRemainingToday=0이면 "스킵"이 아니라 collectorSwitch OFF로 내려 autoLoop 자체를 종료(수집 완전 중단)
//        (3) refine 예산을 quotaRemainingToday로 상한 처리(min)하여 초과 호출 방지
//        (4) BusSegmentCollector에 getLastRefineDiagSummary()가 없을 수 있으므로 리플렉션으로 안전 조회(String)

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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.collector.ApiQuotaManager;
import com.example.demo.collector.BusSegmentCollector;
import com.example.demo.collector.CollectorSwitch;

@RestController
public class CollectorController {

    @Autowired
    private BusSegmentCollector busSegmentCollector;

    @Autowired
    private ApiQuotaManager apiQuotaManager;

    @Autowired
    private CollectorSwitch collectorSwitch;

    private final AtomicInteger intervalMs = new AtomicInteger(60000);
    private final AtomicInteger batchSize = new AtomicInteger(5);

    // ✅ refineOnce(callsBudget) : arrival API 호출 예산
    private final AtomicInteger refineCallsBudgetPerLoop = new AtomicInteger(4);

    private final AtomicInteger lastElapsedMs = new AtomicInteger(0);

    private volatile ScheduledFuture<?> future;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    private static final LocalTime WINDOW_START = LocalTime.of(4, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(23, 30);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "collector-auto-runner");
            t.setDaemon(true);
            return t;
        }
    });

    @GetMapping(value = "/collector/runOnce", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runOnce(@RequestParam(defaultValue = "5") int batch) {

        // ✅ 쿼터가 0이면 스킵
        if (apiQuotaManager != null && apiQuotaManager.getRemainingToday() <= 0) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ok", false);
            res.put("message", "quotaRemainingToday=0 (skip collectOnce)");
            res.put("batchSize", batch);
            res.put("quotaUsedToday", apiQuotaManager.getUsedToday());
            res.put("quotaRemainingToday", apiQuotaManager.getRemainingToday());
            res.put("ts", LocalDateTime.now().toString());
            return res;
        }

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

    @GetMapping(value = "/collector/runRefineOnce", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> runRefineOnce(@RequestParam(defaultValue = "6") int callsBudget) {

        // ✅ callsBudget을 quotaRemainingToday로 상한 처리
        int effectiveBudget = callsBudget;

        if (apiQuotaManager != null) {
            int remaining = apiQuotaManager.getRemainingToday();

            if (remaining <= 0) {
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("ok", false);
                res.put("mode", "REFINE");
                res.put("arrivalCallsBudget", callsBudget);
                res.put("effectiveArrivalCallsBudget", 0);
                res.put("message", "quotaRemainingToday=0 (skip refineOnce)");
                res.put("quotaUsedToday", apiQuotaManager.getUsedToday());
                res.put("quotaRemainingToday", remaining);
                res.put("lastRefineDiag", safeInvokeString(busSegmentCollector, "getLastRefineDiagSummary"));
                return res;
            }

            effectiveBudget = Math.min(callsBudget, remaining);
        }

        long st = System.currentTimeMillis();
        invokeRefineOnceSafely(effectiveBudget);
        int elapsed = (int) (System.currentTimeMillis() - st);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("mode", "REFINE");
        res.put("arrivalCallsBudget", callsBudget);
        res.put("effectiveArrivalCallsBudget", effectiveBudget);
        res.put("elapsedMs", elapsed);

        // ✅ B안 진단: BusSegmentCollector에 메서드가 없을 수도 있으므로 안전 조회
        res.put("lastRefineDiag", safeInvokeString(busSegmentCollector, "getLastRefineDiagSummary"));

        return res;
    }

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

        // ✅ refineOnce 예산(=arrivalCallsBudget) 표시
        res.put("arrivalCallsBudgetPerLoop", refineCallsBudgetPerLoop.get());

        if (apiQuotaManager != null) {
            res.put("quotaUsedToday", apiQuotaManager.getUsedToday());
            res.put("quotaRemainingToday", apiQuotaManager.getRemainingToday());
        }

        res.put("lastElapsedMs", lastElapsedMs.get());
        res.put("inProgress", inProgress.get());

        res.put("cycleCount", safeInvokeInt(busSegmentCollector, "getCycleCount"));
        res.put("visitedRoutesNow", safeInvokeInt(busSegmentCollector, "getVisitedRoutesCount"));
        res.put("rrIndexNow", safeInvokeInt(busSegmentCollector, "getRoundRobinIndex"));

        // ✅ B안 진단(있으면 표시, 없으면 null)
        res.put("lastRefineDiag", safeInvokeString(busSegmentCollector, "getLastRefineDiagSummary"));

        boolean withinWindow = isWithinOperatingWindow();
        res.put("withinWindow", withinWindow);
        res.put("windowStart", WINDOW_START.toString());
        res.put("windowEnd", WINDOW_END.toString());

        res.put("ts", LocalDateTime.now().toString());
        return res;
    }

    private synchronized void startAutoLoop() {

        if (future != null && !future.isCancelled() && !future.isDone()) {
            return;
        }

        future = scheduler.schedule(() -> {

            while (collectorSwitch.isEnabled()) {

                // ✅ cancel(true)로 인터럽트가 걸리면 즉시 탈출
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!isWithinOperatingWindow()) {
                    if (!sleepQuietly(intervalMs.get())) {
                        break;
                    }
                    continue;
                }

                // ✅ 쿼터가 0이면 "스킵"이 아니라 "수집 OFF" 처리하여 루프 자체를 종료한다.
                //    - 쿼터 소진 이후에도 계속 돌며 로그를 찍거나 상태가 running으로 보이는 것을 방지
                if (apiQuotaManager != null && apiQuotaManager.getRemainingToday() <= 0) {
                    collectorSwitch.stop();
                    System.out.println("[COLLECTOR][QUOTA] remaining=0 -> collectorSwitch OFF (autoLoop exit)");
                    break;
                }

                if (!inProgress.compareAndSet(false, true)) {
                    if (!sleepQuietly(intervalMs.get())) {
                        break;
                    }
                    continue;
                }

                long st = System.currentTimeMillis();
                int usedBatch = batchSize.get();

                try {
                    // ✅ 실행 직전에도 인터럽트 반영
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    busSegmentCollector.collectOnce(usedBatch);

                    // ✅ collect 후에도 인터럽트 반영
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    // ✅ refine 예산을 quotaRemainingToday로 상한 처리
                    int budget = refineCallsBudgetPerLoop.get();
                    if (apiQuotaManager != null) {
                        int remaining = apiQuotaManager.getRemainingToday();
                        budget = Math.min(budget, Math.max(remaining, 0));
                    }

                    if (budget > 0) {
                        invokeRefineOnceSafely(budget);
                    }

                } catch (Exception e) {
                    System.out.println("[COLLECTOR][AUTO ERROR] " + e.getMessage());
                } finally {
                    int elapsed = (int) (System.currentTimeMillis() - st);
                    lastElapsedMs.set(elapsed);

                    autoTuneBatch(elapsed);

                    inProgress.set(false);
                }

                if (!sleepQuietly(intervalMs.get())) {
                    break;
                }
            }

        }, 0, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopAutoLoop() {
        if (future != null) {
            try {
                // ✅ 실행 중인 루프 스레드에 인터럽트를 건다(즉시 중단 유도)
                future.cancel(true);
            } catch (Exception ignore) {
            }
            future = null;

            // ✅ 여기서 inProgress를 강제로 false로 내리지 않는다.
            //    실제 작업 중이면 finally에서 정리되며, 강제 변경은 상태 꼬임을 만든다.
        }
    }

    @PostConstruct
    public void autoStartOnBoot() {
        if (collectorSwitch.isEnabled()) {
            startAutoLoop();
        }
    }

    private boolean isWithinOperatingWindow() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(WINDOW_START) && !now.isAfter(WINDOW_END);
    }

    private void autoTuneBatch(int elapsedMs) {
        int before = batchSize.get();
        int b = before;

        if (elapsedMs >= 15000) b = b - 1;
        else if (elapsedMs <= 4000) b = b + 1;

        if (b < 3) b = 3;
        if (b > 10) b = 10;

        batchSize.set(b);

        System.out.println("[COLLECTOR][TUNE] elapsedMs=" + elapsedMs + " batch " + before + " -> " + b);
    }

    private boolean sleepQuietly(int ms) {
        if (ms <= 0) {
            ms = 1;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<String, Object> statusWithMessage(String msg) {
        Map<String, Object> res = status();
        res.put("message", msg);
        return res;
    }

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

    private String safeInvokeString(Object target, String methodName) {
        if (target == null || methodName == null || methodName.trim().isEmpty()) {
            return null;
        }

        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);

            if (v == null) return null;
            return String.valueOf(v);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void invokeRefineOnceSafely(int callsBudget) {
        try {
            Method m = busSegmentCollector.getClass().getMethod("refineOnce", int.class);
            m.invoke(busSegmentCollector, callsBudget);
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
        } catch (Exception ignore) {
        }

        try {
            scheduler.shutdownNow();
        } catch (Exception ignore) {
        }
    }
}

// 수정됨 끝
