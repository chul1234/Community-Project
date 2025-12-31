// 추가됨: 2번 문제(신성동→유성) 최단경로 계산 Service 인터페이스 (DTO 없이 Map 반환)

package com.example.demo.service.path;

import java.util.Map; // JSON 응답을 DTO 없이 Map으로 반환하기 위한 타입

/**
 * 2번 문제(신성동 주민센터 → 유성 시외버스 정류장) 최단경로(최단시간) 계산 서비스
 *
 * - DTO를 사용하지 않으므로, 결과는 Map<String, Object> 형태로 반환한다.
 * - Controller는 이 Service를 호출해 결과(Map)를 그대로 JSON으로 내려준다.
 */
public interface IPathService {

    /**
     * 최단경로(최단시간) 계산을 수행한다.
     *
     * 입력:
     * - fromLat/fromLng : 출발 좌표 (WGS84 위경도)
     * - toLat/toLng     : 도착 좌표 (WGS84 위경도)
     * - snapRadiusM     : 출발/도착을 정류장/정거장에 스냅시키는 반경(m), 정책 기본 500m
     * - maxTransfers    : 허용 환승 횟수(0=직행만, 1=1회 환승까지, ...). 기본 2
     *
     * 출력(Map):
     * - totalMinutes : 총 소요시간(분)
     * - segments     : 구간 리스트(List<Map<String,Object>>)
     *                 각 구간은 mode(WALK/BUS/TRAM), minutes, routeId(있으면), points(좌표 리스트) 등을 포함한다.
     *
     * ※ 현재 1단계 구현 범위:
     * - BUS(segment_weight) + 출발/도착 도보 스냅 기반
     * - TRAM/arrtime/환승 정책은 이후 단계에서 확장
     */
    Map<String, Object> solve(double fromLat, double fromLng, double toLat, double toLng, double snapRadiusM, int maxTransfers);
}

// 추가됨 끝
