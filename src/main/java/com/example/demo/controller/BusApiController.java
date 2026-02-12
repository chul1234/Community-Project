// 수정됨: arrival API가 429(Quota Exceeded)일 때 ApiQuotaManager를 소진 처리하고 collectorSwitch OFF로 내려 수집 자동 루프를 즉시 중단

package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.example.demo.collector.ApiQuotaManager;
import com.example.demo.collector.CollectorSwitch;

/**
 * TAGO (국토교통부) 버스 노선 정보 + 정류장 목록 + 버스 위치 + 도착정보 조회 컨트롤러
 * - 도시 코드는 대전(25) 고정
 * - 응답은 TAGO JSON 문자열 그대로 프록시해서 반환
 */
@RestController
public class BusApiController {

    // TAGO 공공데이터 서비스키 (URL 인코딩된 형태 그대로 사용)
    // TAGO 공공데이터 서비스키 (URL 인코딩된 형태 그대로 사용)
    @Value("${tago.service.key}")
    private String serviceKey;

    // 대전 도시코드
    private static final String CITY_CODE = "25";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 일일 호출 예산 관리자(있으면 적용, 없으면 null)
     * - arrival API 트래픽 절감/차단을 위해 전역에서 공통 적용
     */
    @Autowired(required = false)
    private ApiQuotaManager apiQuotaManager;

    // ✅ 수집 자동 루프가 켜져 있는 상태에서 외부 429(Quota Exceeded)가 확정되면 즉시 OFF로 내려
    //    더 이상의 불필요한 호출/로그 스팸을 막는다.
    @Autowired(required = false)
    private CollectorSwitch collectorSwitch;

    /**
     * 1) 버스 번호 → 노선 목록 조회 (getRouteNoList)
     *
     * 예: /api/bus/routes?routeNo=202
     */
    @CrossOrigin
    @GetMapping("/api/bus/routes")
    public String getRoutesByNumber(@RequestParam("routeNo") String routeNo) {

        String url = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteNoList"
                + "?serviceKey=" + serviceKey
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
                + "?serviceKey=" + serviceKey
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
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"route-stops proxy failed\",\"message\":\""
                            + ex.getMessage().replace("\"", "\\\"") + "\"}");
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
                + "?serviceKey=" + serviceKey
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&routeId=" + routeId
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        return restTemplate.getForObject(url, String.class);
    }

    /**
     * 4) 정류소별 도착 예정 버스 목록 조회 (getSttnAcctoArvlPrearngeInfoList)
     *
     * 예: /api/bus/arrivals?nodeId=DJB8001793&numOfRows=20
     */
    @CrossOrigin
    @GetMapping("/api/bus/arrivals")
    public ResponseEntity<String> getArrivalsByStop(
            @RequestParam(value = "nodeId", required = false) String nodeId,
            @RequestParam(value = "stopId", required = false) String stopId,
            @RequestParam(value = "pageNo", defaultValue = "1") String pageNo,
            @RequestParam(value = "numOfRows", defaultValue = "20") String numOfRows
    ) {

        String finalNodeId = (nodeId != null && !nodeId.isBlank()) ? nodeId : stopId;
        if (finalNodeId == null || finalNodeId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"BAD_REQUEST\",\"message\":\"nodeId(stopId)가 필요합니다.\"}");
        }

        // 1) 서버 단에서 일일 호출 예산을 먼저 차단한다.
        //    (프론트에서 새로고침/반복 호출해도, 여기서 더 이상 외부 API를 때리지 않도록)
        if (apiQuotaManager != null && !apiQuotaManager.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"API_QUOTA_EXCEEDED\",\"message\":\"오늘 TAGO API 호출 허용량을 초과했습니다. (서버에서 차단됨)\"}");
        }

        String url = "http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"
                + "?serviceKey=" + serviceKey
                + "&_type=json"
                + "&cityCode=" + CITY_CODE
                + "&nodeId=" + finalNodeId
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        try {
            String response = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } catch (HttpStatusCodeException ex) {
            // 2) 외부에서 429가 떨어졌으면 "오늘은 끝"으로 판단하고, 이후 호출을 즉시 차단한다.
            if (apiQuotaManager != null && ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                apiQuotaManager.markExhaustedForToday();
                if (collectorSwitch != null) {
                    collectorSwitch.stop();
                }
            }
            // TAGO가 내려준 에러 본문을 그대로 반환해서 원인을 프론트(Network Response)에서 확인 가능하게 한다.
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"arrivals proxy failed\",\"message\":\""
                            + ex.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    /**
     * 5) 정류장 이름으로 정류장 목록 조회 (BusSttnInfoInqireService/getSttnNoList)
     *
     * 예: /api/bus/stops-by-name?nodeName=정부청사
     */
    @CrossOrigin
    @GetMapping("/api/bus/stops-by-name")
    public String getStopsByName(
            @RequestParam("nodeName") String nodeName,
            @RequestParam(value = "pageNo", defaultValue = "1") String pageNo,
            @RequestParam(value = "numOfRows", defaultValue = "100") String numOfRows
    ) {

        String url = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnNoList"
                + "?serviceKey=" + serviceKey
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
                + "?serviceKey=" + serviceKey
                + "&_type=json"
                + "&gpsLati=" + lat
                + "&gpsLong=" + lng
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;

        return restTemplate.getForObject(url, String.class);
    }
}

// 수정됨 끝
