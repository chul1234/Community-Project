// 수정됨: 웹페이지가 꺼져도(브라우저 닫아도) 서버가 살아 있으면 자동 수집이 계속되도록 변경
//        - 서버 부팅 시 자동으로 수집 ON 시작(@PostConstruct)
//        - /collector/toggle : 수집 ON/OFF 토글(버튼 1개)
//        - /collector/status : ON/OFF + batch/interval + 운행 시간창(04:00~23:30) 상태(withinWindow) 제공
//        - CollectorSwitch(ON/OFF 스위치)를 분리하여 수집 제어를 단일 책임으로 관리

package com.example.demo.controller;

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

@RestController
public class CollectorController {

    @Autowired
    private BusSegmentCollector busSegmentCollector;

    @Autowired
    private CollectorSwitch collectorSwitch;

    // =========================
    // 자동 수집(서버 백그라운드) 상태
    // =========================
    // 주의: 실제 ON/OFF는 CollectorSwitch가 기준이다.
    //       (프론트는 "running" 필드를 보므로 status에선 switch 상태를 그대로 내려준다.)

    // interval(다음 실행까지 쉬는 시간)
    private final AtomicInteger intervalMs = new AtomicInteger(8000);

    // ✅ batchSize는 서버가 자동으로 조절
    private final AtomicInteger batchSize = new AtomicInteger(5);

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
        res.put("lastElapsedMs", lastElapsedMs.get());
        res.put("inProgress", inProgress.get());

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
        return !now.isBefore(WINDOW_START) && now.isBefore(WINDOW_END);
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
