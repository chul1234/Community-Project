// 수정됨: isExhaustedToday() 추가(remaining=0 상태를 명확히 판정)하여 수집 루프/외부 호출 경로에서 즉시 중단 가능

package com.example.demo.collector;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * 공공데이터포털/외부 API 호출량(일일 10,000) 상한을 지키기 위한 간단한 쿼터 매니저이다.
 *
 * - 날짜가 바뀌면 자동으로 used를 0으로 리셋한다.
 * - tryConsume(n)이 true일 때만 실제 API 호출을 수행하도록 강제한다.
 */
@Component
public class ApiQuotaManager {

    // 일일 트래픽 상한(사용자 요구사항: 10,000)
    private static final int DAILY_LIMIT = 10000;

    private volatile LocalDate currentDate = LocalDate.now();
    private final AtomicInteger usedToday = new AtomicInteger(0);

    private synchronized void rolloverIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            currentDate = today;
            usedToday.set(0);
        }
    }

    /**
     * n번 호출을 사용할 수 있으면 used를 증가시키고 true를 반환한다.
     * 사용할 수 없으면 false를 반환한다.
     */
    public boolean tryConsume(int n) {
        if (n <= 0) return true;
        rolloverIfNeeded();

        while (true) {
            int used = usedToday.get();
            if (used + n > DAILY_LIMIT) return false;
            if (usedToday.compareAndSet(used, used + n)) return true;
        }
    }

    public int getUsedToday() {
        rolloverIfNeeded();
        return usedToday.get();
    }

    public int getRemainingToday() {
        rolloverIfNeeded();
        int used = usedToday.get();
        return Math.max(0, DAILY_LIMIT - used);
    }

    /**
     * 오늘 기준으로 호출 가능 잔여량이 0인지(=쿼터 소진 상태) 반환한다.
     *
     * @return true면 오늘은 더 이상 외부 API 호출을 하면 안 된다.
     */
    public boolean isExhaustedToday() {
        return getRemainingToday() <= 0;
    }

    /**
     * 외부 API에서 "quota exceeded"(429 등)가 발생했을 때, 남은 호출을 강제로 0으로 만든다.
     * 공공데이터포털은 초과 이후 동일 키로 계속 429가 날 수 있으므로, 그날은 즉시 중단시키는 용도이다.
     */
    public void markExhaustedForToday() {
        rolloverIfNeeded();
        usedToday.set(DAILY_LIMIT);
    }
}

// 수정됨 끝
