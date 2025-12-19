// 수정됨: "데이터 수집" 버튼 1개로 ON/OFF 토글 + 서버 자동 순환(라운드로빈) + batchSize 자동 조절(적응형)
//        - /collector/toggle : ON이면 OFF로, OFF면 ON으로 전환
//        - /collector/status : 현재 ON/OFF, 현재 batchSize, intervalMs, lastElapsedMs 제공
//        - 자동 배치: 실행 시간이 길면 batch 감소, 짧으면 batch 증가 (3~10 범위)
//        - 개선: intervalMs 변경이 즉시 반영되도록 fixedDelay 스케줄 대신 "단일 루프 + sleep" 구조로 안정화
//        - 개선: 토글 연타/중복 실행 방지(단일 future 보장, inProgress 보호 유지)

package com.example.demo.controller;

import java.time.LocalDateTime;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.collector.BusSegmentCollector;

@RestController
public class CollectorController {

    @Autowired
    private BusSegmentCollector busSegmentCollector;

    // =========================
    // 자동 수집(서버 백그라운드) 상태
    // =========================
    private final AtomicBoolean running = new AtomicBoolean(false);

    // interval(다음 실행까지 쉬는 시간)
    private final AtomicInteger intervalMs = new AtomicInteger(5000);

    // ✅ batchSize는 서버가 자동으로 조절
    private final AtomicInteger batchSize = new AtomicInteger(5);

    // 마지막 실행 시간(ms) - 배치 자동 조절 판단 기준
    private final AtomicInteger lastElapsedMs = new AtomicInteger(0);

    // 스케줄 취소 핸들
    private volatile ScheduledFuture<?> future;

    // 수집 실행이 겹치지 않게 보호(혹시라도 호출이 중첩되면 스킵)
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

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

        // OFF -> ON
        if (running.compareAndSet(false, true)) {
            startAutoLoop();
            return statusWithMessage("collector auto started");
        }

        // ON -> OFF
        stopAutoLoop();
        return statusWithMessage("collector auto stopped");
    }

    @GetMapping(value = "/collector/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> status() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("running", running.get());
        res.put("batchSize", batchSize.get());
        res.put("intervalMs", intervalMs.get());
        res.put("lastElapsedMs", lastElapsedMs.get());
        res.put("inProgress", inProgress.get());
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

            while (running.get()) {

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
        running.set(false);

        if (future != null) {
            try { future.cancel(false); } catch (Exception ignore) {}
            future = null;
        }
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
