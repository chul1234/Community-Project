// 추가됨: 2번 문제(신성동→유성) 최단경로 계산 API 컨트롤러 (DTO 없이 Map 반환)

package com.example.demo.controller;

import java.util.Map; // DTO 없이 JSON 응답을 Map으로 반환하기 위한 타입

import org.springframework.beans.factory.annotation.Autowired; // DI
import org.springframework.web.bind.annotation.CrossOrigin; // CORS 허용
import org.springframework.web.bind.annotation.GetMapping; // GET 매핑
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러

import com.example.demo.service.path.IPathService; // 경로 계산 서비스

/**
 * 최단경로(최단시간) 계산 API
 *
 * - "TAGO 원본 데이터"가 아니라, 서버가 계산한 "최단경로 결과"를 반환한다.
 * - 그래프는 내부 계산용이며, 지도에는 결과 경로만 표시한다.
 * - DTO를 사용하지 않으므로 Map<String,Object>를 그대로 반환한다.
 */
@RestController // REST API 컨트롤러 등록
public class PathController {

    @Autowired // Service 주입
    private IPathService pathService;

    /**
     * 최단경로 계산
     *
     * 호출 예시:
     * /api/path/solve?fromLat=36.3&fromLng=127.3&toLat=36.35&toLng=127.34&snapRadiusM=500
     *
     * - 현재 1단계 구현:
     *   BUS(segment_weight) + 출발/도착 도보 스냅(500m)
     * - TRAM/arrtime/환승 도보 연결은 이후 단계에서 확장
     *
     * @param fromLat 출발 위도(WGS84)
     * @param fromLng 출발 경도(WGS84)
     * @param toLat 도착 위도(WGS84)
     * @param toLng 도착 경도(WGS84)
     * @param snapRadiusM 스냅 반경(m), 기본 500m
     * @return 최단경로 결과(Map) - totalMinutes, segments 등 포함
     */
    @CrossOrigin // 프론트 호출 편의를 위해 CORS 허용(프로젝트 기존 방식과 동일하게 운용 가능)
    @GetMapping("/api/path/solve") // 경로 계산 엔드포인트
    public Map<String, Object> solve(
        @RequestParam("fromLat") double fromLat, // 출발 위도
        @RequestParam("fromLng") double fromLng, // 출발 경도
        @RequestParam("toLat") double toLat, // 도착 위도
        @RequestParam("toLng") double toLng, // 도착 경도
        @RequestParam(value = "snapRadiusM", defaultValue = "500") double snapRadiusM // 스냅 반경(m)
    ) {

        // Service에 계산 위임 후 결과(Map)를 그대로 반환(JSON 자동 변환)
        return pathService.solve(fromLat, fromLng, toLat, toLng, snapRadiusM);
    }
}

// 추가됨 끝
