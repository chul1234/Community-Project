// 수정됨: /api/bus/route-stops 호출 시 RestTemplate 예외로 서버가 500을 내리며 라인 좌표(정류장 목록)가 전달되지 않는 문제 대응
//        - TAGO가 4xx/5xx를 반환해도 예외로 터지지 않도록 처리
//        - 에러 응답 본문을 그대로 프론트로 반환하여 Network/Response에서 원인 확인 가능하게 함

package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
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
    public ResponseEntity<String> getRouteStops(@RequestParam("routeId") String routeId) {

        String url = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteAcctoThrghSttnList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&routeId=" + routeId
                + "&pageNo=1"
                + "&numOfRows=150";

        try {
            String body = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException ex) {
            // TAGO가 4xx/5xx를 내려도 서버가 500으로 죽지 않게 하고,
            // 실제 에러 본문을 그대로 반환해서 프론트(Network Response)에서 원인을 확인 가능하게 한다.
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            // 예상치 못한 예외도 메시지를 반환해 원인 추적 가능하게 한다.
            return ResponseEntity.status(500).body("{\"error\":\"route-stops proxy failed\",\"message\":\"" + ex.getMessage() + "\"}");
        }
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

        String url = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnNoList"
                + "?serviceKey=" + SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&nodeNm=" + nodeName
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        System.out.println("DEBUG GET URL = " + url);

        return restTemplate.getForObject(url, String.class);
    }

    /**
     * 6) 좌표 기반 근접 정류장 목록 조회 (getCrdntPrxmtSttnList)
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
