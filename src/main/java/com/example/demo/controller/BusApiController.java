// 수정됨: TAGO 노선 + 정류장 + 버스 위치(JSON) 전용 컨트롤러 (route-stops에 numOfRows 추가)

package com.example.demo.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * TAGO (국토교통부) 버스 노선 정보 + 정류장 목록 + 버스 위치 조회 컨트롤러
 * - 도시 코드는 대전(25) 고정
 * - 응답은 TAGO JSON 문자열 그대로 프록시해서 반환
 */
@RestController
public class BusApiController {

    // TAGO 공공데이터 서비스키 (URL 인코딩된 형태 그대로 사용)
    private static final String SERVICE_KEY =
            "ff623cef3aa0e011104003d8973105076b9f4ce098a93e4b6de36a9f2560529c";

    // 대전 도시코드
    private static final String CITY_CODE = "25";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 1) 버스 번호 → 노선 목록 조회 (getRouteNoList)
     *
     * 예: /api/bus/routes?routeNo=202
     */
    @CrossOrigin
    @GetMapping("/api/bus/routes")
    public String getRoutesByNumber(@RequestParam("routeNo") String routeNo) {

        String url = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteNoList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&routeNo=" + routeNo;

        return restTemplate.getForObject(url, String.class);
    }

    /**
     * 2) 노선ID → 노선 경유 정류장 목록 조회 (getRouteAcctoThrghSttnList)
     *    → 한 번에 최대 100개까지 조회
     *
     * 예: /api/bus/route-stops?routeId=DJB30300052
     */
    @CrossOrigin
    @GetMapping("/api/bus/route-stops")
    public String getRouteStops(@RequestParam("routeId") String routeId) {

        String url = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteAcctoThrghSttnList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&routeId=" + routeId
                + "&pageNo=1"          // ★ 추가
                + "&numOfRows=150";    // ★ 추가

        return restTemplate.getForObject(url, String.class);
    }

    /**
     * 3) 노선ID 기준 버스 위치 조회 (getRouteAcctoBusLcList)
     *
     * 예: /api/bus/locations?routeId=DJB30300052&pageNo=1&numOfRows=100
     */
    @CrossOrigin
    @GetMapping("/api/bus/locations")
    public String getBusLocations(
            @RequestParam("routeId") String routeId,
            @RequestParam(value = "pageNo", defaultValue = "1") String pageNo,
            @RequestParam(value = "numOfRows", defaultValue = "100") String numOfRows
    ) {

        String url = "http://apis.data.go.kr/1613000/BusLcInfoInqireService/getRouteAcctoBusLcList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&routeId=" + routeId
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        return restTemplate.getForObject(url, String.class);
    }
}

// 수정됨 끝
