// 수정됨: TAGO 노선 + 정류장 + 버스 위치(JSON) + 정류장 도착정보 API + 정류장 이름 검색 API 추가

package com.example.demo.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * TAGO (국토교통부) 버스 노선 정보 + 정류장 목록 + 버스 위치 + 도착정보 조회 컨트롤러
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
                + "&pageNo=1"
                + "&numOfRows=150";

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

    /**
     * 4) 정류소별 도착 예정 버스 목록 조회
     *    (getSttnAcctoArvlPrearngeInfoList)
     *
     * 예: /api/bus/arrivals?nodeId=DJB8001793
     */
    @CrossOrigin
    @GetMapping("/api/bus/arrivals")
    public String getArrivalsByStop(
            @RequestParam("nodeId") String nodeId,
            @RequestParam(value = "pageNo", defaultValue = "1") String pageNo,
            @RequestParam(value = "numOfRows", defaultValue = "20") String numOfRows
    ) {

        String url = "http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&nodeId=" + nodeId
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        return restTemplate.getForObject(url, String.class);
    }

    /**
     * 5) 정류장 이름으로 정류장 목록 조회
     *    BusSttnInfoInqireService/getSttnNoList
     *
     * 예: /api/bus/stops-by-name?nodeName=정부청사
     */
    // 수정됨: 정류장 이름 검색 시 URLEncoder 제거하여 원본 문자열 그대로 전달

@CrossOrigin
@GetMapping("/api/bus/stops-by-name")
public String getStopsByName(
        @RequestParam("nodeName") String nodeName,
        @RequestParam(value = "pageNo", defaultValue = "1") String pageNo,
        @RequestParam(value = "numOfRows", defaultValue = "100") String numOfRows
) {

    // ※ 기존: String encodedName = URLEncoder.encode(nodeName, StandardCharsets.UTF_8);
    // TAGO API가 실제로 URL 인코딩을 이중으로 기대하지 않을 가능성이 있어 제거함

    String url = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnNoList"
            + "?serviceKey=" + SERVICE_KEY
            + "&_type=json"
            + "&cityCode=" + CITY_CODE
            + "&nodeNm=" + nodeName   // ★ 인코딩 제거!
            + "&pageNo=" + pageNo
            + "&numOfRows=" + numOfRows;

    System.out.println("DEBUG GET URL = " + url);

    return restTemplate.getForObject(url, String.class);
}

    /**
     * 6) 좌표 기반 근접 정류장 목록 조회 (getCrdntPrxmtSttnList)
     *
     * - 최단경로 결과(segments)의 BUS 정류장 hover에서 "정류장 이름"을 표시하기 위해 사용한다.
     * - segment_weight에는 정류장 이름이 없어서, (lat,lng) 기준으로 근처 정류장 목록을 받아
     *   nodeid가 일치하는 항목의 nodenm을 프론트에서 매칭한다.
     *
     * 예: /api/bus/stops-nearby?lat=36.36&lng=127.37
     */
    @CrossOrigin
    @GetMapping("/api/bus/stops-nearby")
    public String getStopsNearby(
            @RequestParam("lat") String lat,
            @RequestParam("lng") String lng,
            @RequestParam(value = "pageNo", defaultValue = "1") String pageNo,
            @RequestParam(value = "numOfRows", defaultValue = "20") String numOfRows
    ) {

        String url = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&gpsLati=" + lat
                + "&gpsLong=" + lng
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        return restTemplate.getForObject(url, String.class);
    }

}

// 수정됨 끝
