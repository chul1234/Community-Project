// 추가됨: 수집 ON/OFF 상태를 단일 책임으로 관리하는 스위치
//        - CollectorController 등 여러 곳에서 공통 사용 가능
//        - enabled=true면 수집 허용, false면 수집 중지

package com.example.demo.collector;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * 수집(Collector) 실행 여부를 제어하는 스위치 컴포넌트이다.
 *
 * - enabled=true  : 수집 실행 허용
 * - enabled=false : 수집 중지
 *
 * 서버가 살아 있어도(enabled=false)면 수집은 수행되지 않는다.
 */
@Component
public class CollectorSwitch {

    // 수집 활성화 여부(기본값: ON)
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    /**
     * 현재 수집이 활성화 상태인지 반환한다.
     *
     * @return true면 수집 ON, false면 수집 OFF
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * 수집 상태를 토글한다.
     *
     * @return 토글 후 상태(true=ON, false=OFF)
     */
    public boolean toggle() {
        while (true) {
            boolean cur = enabled.get();
            boolean next = !cur;
            if (enabled.compareAndSet(cur, next)) {
                return next;
            }
        }
    }

    /**
     * 수집을 강제로 ON으로 만든다.
     */
    public void start() {
        enabled.set(true);
    }

    /**
     * 수집을 강제로 OFF로 만든다.
     */
    public void stop() {
        enabled.set(false);
    }
}

// 추가됨 끝
