// 수정됨: BUS 구간(nodeIds)에 대한 정류장명(nodeNames)이 null로 내려오는 문제 해결
//        - /api/path/solve 응답의 segments[].nodeNames를 서버에서 채워서 반환하도록 개선
//        - BUS 구간의 nodeId -> 정류장명은 TAGO '노선 경유 정류장 목록(route-stops)'을 routeId 단위로 1회 조회 후 메모리 캐시
//        - 캐시 미스/오류 시에는 기존처럼 null을 유지(프론트에서 빈 문자열 등으로 처리 가능)

package com.example.demo.service.path.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.dao.SegmentWeightDAO;
import com.example.demo.service.path.IPathService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PathServiceImpl implements IPathService {

    // =========================
    // 정책 상수
    // =========================

    // 도보 속도: 1.3m/s (성인 평균)
    private static final double WALK_SPEED_MPS = 1.3;
    // 트램 속도: 35km/h (표정속도)
    private static final double TRAM_SPEED_KMPH = 35.0;

    // 환승/재승차 패널티: 1회당 고정 페널티(분)
    private static final double TRANSFER_PENALTY_MIN = 4.0;

    // START/END 가상 노드 ID
    private static final String START_ID = "__START__";
    private static final String END_ID = "__END__";

    @Autowired
    private SegmentWeightDAO segmentWeightDAO;

    // =========================
    // BUS 정류장명 캐시(서버에서 nodeNames 채우기)
    // =========================

    // TAGO 공공데이터 서비스키 (기존 BusApiController와 동일한 값 사용)
    private static final String TAGO_SERVICE_KEY =
            "ff623cef3aa0e011104003d8973105076b9f4ce098a93e4b6de36a9f2560529c";

    // 대전 도시코드
    private static final String TAGO_CITY_CODE = "25";

    // routeId -> (nodeId -> nodeName) 캐시
    private static final Map<String, Map<String, String>> BUS_ROUTE_STOPNAME_CACHE = new ConcurrentHashMap<>();
    
    // routeId -> routeNo 캐시 (예: DJB30300052 -> "105")
    private static final Map<String, String> BUS_ROUTE_NUMBER_CACHE = new ConcurrentHashMap<>();

    private final RestTemplate busNameRestTemplate = new RestTemplate();
    private final ObjectMapper busNameObjectMapper = new ObjectMapper();

    // ===============================================================
    // [Step 1] 트램 정거장 데이터 (내부 클래스 및 초기화)
    // ===============================================================
    private static class TramStation {
        int id;
        String name;
        double lat, lng;

        public TramStation(int id, String name, double lat, double lng) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private List<TramStation> tramStations;

    // 트램 데이터 초기화 (JS 데이터 TRAM_ROUTE_FULL_HD의 station 타입 매핑)
    private void initTramData() {
        if (tramStations != null) return;
        tramStations = new ArrayList<>();

        // 1구간
        tramStations.add(new TramStation(244, "연축(차량기지)", 36.39229, 127.42084));
        tramStations.add(new TramStation(243, "회덕", 36.37892, 127.42668));
        tramStations.add(new TramStation(242, "읍내", 36.37191, 127.42863));
        
        // 2구간
        tramStations.add(new TramStation(241, "법동", 36.36633, 127.43022));
        tramStations.add(new TramStation(212, "중리네거리", 36.35895, 127.42584)); // 순환 분기점
        
        // 3구간
        tramStations.add(new TramStation(213, "용전", 36.35873, 127.41787));
        tramStations.add(new TramStation(214, "한남대", 36.35815, 127.41002));
        tramStations.add(new TramStation(215, "오정농수산물", 36.35762, 127.40089));
        
        // 4구간
        tramStations.add(new TramStation(216, "수정타운", 36.35765, 127.39524));
        tramStations.add(new TramStation(217, "창업진흥원", 36.3577, 127.3875));
        tramStations.add(new TramStation(218, "정부청사역", 36.35876, 127.37947));
        
        // 5구간
        tramStations.add(new TramStation(219, "청사북문", 36.365, 127.3795));
        tramStations.add(new TramStation(220, "예술의전당", 36.37, 127.3795));
        tramStations.add(new TramStation(221, "엑스포과학공원", 36.37406, 127.37817));
        
        // 6구간
        tramStations.add(new TramStation(222, "KAIST", 36.37059, 127.37214));
        tramStations.add(new TramStation(223, "유성구청", 36.36641, 127.36592));
        
        // 7구간
        tramStations.add(new TramStation(224, "충남대", 36.36204, 127.34531));
        tramStations.add(new TramStation(225, "유성온천역", 36.35981, 127.3437));
        tramStations.add(new TramStation(226, "상대", 36.35065, 127.34027));
        tramStations.add(new TramStation(227, "원신흥", 36.34511, 127.34023));
        
        // 8구간
        tramStations.add(new TramStation(228, "목원대입구", 36.339, 127.3365));
        tramStations.add(new TramStation(229, "도안고", 36.33212, 127.33282));
        tramStations.add(new TramStation(230, "목원대", 36.326, 127.3328));
        tramStations.add(new TramStation(231, "용소", 36.31927, 127.33309));
        tramStations.add(new TramStation(232, "가수원네거리", 36.30294, 127.33479));
        
        // 9구간
        tramStations.add(new TramStation(245, "진잠", 36.29932, 127.32423));
        tramStations.add(new TramStation(233, "롯데시네마", 36.30151, 127.33547));
        tramStations.add(new TramStation(234, "대전가원학교", 36.30357, 127.34618));
        tramStations.add(new TramStation(235, "가수원교회", 36.30551, 127.35484));
        
        // 10구간
        tramStations.add(new TramStation(236, "가수원교", 36.30739, 127.36376));
        tramStations.add(new TramStation(237, "도마네거리", 36.31268, 127.37919));
        
        // 11구간
        tramStations.add(new TramStation(238, "유등교", 36.31507, 127.38455));
        
        // 12구간
        tramStations.add(new TramStation(239, "유천", 36.31618, 127.38879));
        tramStations.add(new TramStation(240, "오류", 36.31886, 127.39934));
        tramStations.add(new TramStation(201, "서대전역", 36.32109, 127.40789));
        tramStations.add(new TramStation(202, "서대전네거리", 36.32237, 127.41233));
        tramStations.add(new TramStation(203, "대사", 36.31822, 127.41782));
        tramStations.add(new TramStation(204, "부사", 36.3178, 127.42145));
        tramStations.add(new TramStation(205, "인동", 36.32067, 127.43509));
        
        // 13구간
        tramStations.add(new TramStation(206, "대전역", 36.33093, 127.43276));
        tramStations.add(new TramStation(207, "중앙동 행정 복지 센터", 36.33354, 127.43925));
        tramStations.add(new TramStation(208, "신흥", 36.32985, 127.44323));
        tramStations.add(new TramStation(209, "우송대(자양)", 36.34068, 127.44887));
        
        // 14구간
        tramStations.add(new TramStation(210, "동부네거리", 36.35111, 127.44206));
        tramStations.add(new TramStation(211, "동부네거리", 36.35823, 127.43355)); 
        // 211은 순환선 구조상 212(중리네거리)와 다시 연결되어야 함
    }

    /**
     * 최단경로(최단시간) 계산 서비스 구현
     * * 1. DB에서 버스 구간(segment_weight) 전체 로드
     * * 2. 트램 구간 추가 및 버스-트램 환승 연결 [추가됨]
     * * 3. 출발/도착지 주변 정류장을 도보(WALK)로 연결 (Snap)
     * * 4. 다익스트라 알고리즘으로 최단 시간 경로 탐색
     */
    @Override
    public Map<String, Object> solve(double fromLat, double fromLng, double toLat, double toLng, double snapRadiusM, int maxTransfers) {

        // 0. 트램 데이터 로드
        initTramData();

        // ---------------------------------------------------------
        // (1) DB에서 BUS 구간 전부 로드
        // ---------------------------------------------------------
        List<Map<String, Object>> busSegments = segmentWeightDAO.findAllBusSegments();

        // ---------------------------------------------------------
        // (2) 정류장 노드 좌표 맵 + 인접리스트(그래프) 구성
        // ---------------------------------------------------------
        Map<String, StopPoint> stopPointMap = new HashMap<>(); // stopId -> 좌표 객체
        Map<String, List<Edge>> graph = new HashMap<>();       // nodeId -> outgoing edges

        // (2-1) BUS 구간을 그래프로 변환
        for (Map<String, Object> r : busSegments) {
            if (r == null) continue;

            String fromId = asString(r.get("from_id"));
            String toId = asString(r.get("to_id"));
            String routeId = asString(r.get("route_id"));

            int updowncd = asInt(r.get("updowncd"), -1);

            if (fromId == null || toId == null) continue;

            double fromLatStop = asDouble(r.get("from_lat"));
            double fromLngStop = asDouble(r.get("from_lng"));
            double toLatStop = asDouble(r.get("to_lat"));
            double toLngStop = asDouble(r.get("to_lng"));

            double travelSecAvg = asDouble(r.get("travel_sec_avg"));

            // 시간(분) 변환. 0초거나 오류값이면 최소 0.1분으로 보정
            double minutes = Math.max(0.1, travelSecAvg / 60.0);

            // 정류장 좌표 등록 (중복 시 기존 값 유지)
            stopPointMap.putIfAbsent(fromId, new StopPoint(fromId, fromLatStop, fromLngStop));
            stopPointMap.putIfAbsent(toId, new StopPoint(toId, toLatStop, toLngStop));

            // 그래프 간선 추가 (BUS는 실제 운행처럼 유향: from_id -> to_id만 허용)
            addEdge(graph, fromId, new Edge("BUS", routeId, fromId, toId, minutes, updowncd));
        }

        // ---------------------------------------------------------
        // (2-2) [추가] 트램 그래프 구성 (인접 정거장 연결 + 순환 연결)
        // ---------------------------------------------------------
        for (int i = 0; i < tramStations.size(); i++) {
            TramStation curr = tramStations.get(i);
            String currId = "TRAM_" + curr.id;

            // 트램 정거장 좌표 등록
            stopPointMap.put(currId, new StopPoint(currId, curr.lat, curr.lng));

            // 1. 다음 정거장과 연결 (i -> i+1)
            if (i < tramStations.size() - 1) {
                TramStation next = tramStations.get(i + 1);
                String nextId = "TRAM_" + next.id;

                double distM = haversineMeters(curr.lat, curr.lng, next.lat, next.lng);
                double min = distM / (TRAM_SPEED_KMPH * 1000.0 / 60.0);

                addEdge(graph, currId, new Edge("TRAM", "2호선", currId, nextId, min, -1));
                addEdge(graph, nextId, new Edge("TRAM", "2호선", nextId, currId, min, -1));
            }
            
            // 2. 순환 연결: 마지막 '211 동부네거리' -> '212 중리네거리' 연결
            if (curr.id == 211) {
                // 리스트에서 212번 정거장을 찾음
                TramStation jungri = tramStations.stream()
                        .filter(s -> s.id == 212)
                        .findFirst()
                        .orElse(null);
                
                if (jungri != null) {
                    String nextId = "TRAM_" + jungri.id;
                    double distM = haversineMeters(curr.lat, curr.lng, jungri.lat, jungri.lng);
                    double min = distM / (TRAM_SPEED_KMPH * 1000.0 / 60.0);

                    // 양방향 연결 (순환선)
                    addEdge(graph, currId, new Edge("TRAM", "2호선", currId, nextId, min, -1));
                    addEdge(graph, nextId, new Edge("TRAM", "2호선", nextId, currId, min, -1));
                }
            }
        }

        // ---------------------------------------------------------
        // (2-3) [추가] 환승 연결 (버스 ↔ 트램 500m 이내)
        // ---------------------------------------------------------
        List<StopPoint> busStops = new ArrayList<>();
        for (StopPoint sp : stopPointMap.values()) {
            if (!sp.id.startsWith("TRAM_")) {
                busStops.add(sp);
            }
        }

        for (TramStation ts : tramStations) {
            String tramNodeId = "TRAM_" + ts.id;

            for (StopPoint bs : busStops) {
                double dist = haversineMeters(ts.lat, ts.lng, bs.lat, bs.lng);

                // 500m 이내면 환승 가능 (도보)
                if (dist <= 500.0) {
                    double walkMin = metersToWalkMinutes(dist);

                    // 버스 -> 트램
                    addEdge(graph, bs.id, new Edge("WALK", "Transfer", bs.id, tramNodeId, walkMin, -1));
                    // 트램 -> 버스
                    addEdge(graph, tramNodeId, new Edge("WALK", "Transfer", tramNodeId, bs.id, walkMin, -1));
                }
            }
        }

        // ---------------------------------------------------------
        // (3) START/END 가상 노드 좌표 등록
        // ---------------------------------------------------------
        stopPointMap.put(START_ID, new StopPoint(START_ID, fromLat, fromLng));
        stopPointMap.put(END_ID, new StopPoint(END_ID, toLat, toLng));

        // ---------------------------------------------------------
        // (4) 출발/도착 도보 스냅 (Snap)
        // ---------------------------------------------------------
        for (StopPoint sp : stopPointMap.values()) {
            if (sp == null) continue;
            
            // 자기 자신(START/END)은 건너뜀
            if (Objects.equals(sp.id, START_ID) || Objects.equals(sp.id, END_ID)) {
                continue;
            }

            // [출발지 -> 정류장] 거리 계산
            double dStart = haversineMeters(fromLat, fromLng, sp.lat, sp.lng);
            if (dStart <= snapRadiusM) {
                double walkMin = metersToWalkMinutes(dStart);
                addEdge(graph, START_ID, new Edge("WALK", "Start", START_ID, sp.id, walkMin, -1));
            }

            // [정류장 -> 도착지] 거리 계산
            double dEnd = haversineMeters(sp.lat, sp.lng, toLat, toLng);
            if (dEnd <= snapRadiusM) {
                double walkMin = metersToWalkMinutes(dEnd);
                addEdge(graph, sp.id, new Edge("WALK", "End", sp.id, END_ID, walkMin, -1));
            }
        }

        // ---------------------------------------------------------
        // (5) 다익스트라로 최단시간 탐색
        // ---------------------------------------------------------
        // ---------------------------------------------------------
        // (5) 다익스트라로 모든 승차 횟수별 최적 경로 탐색
        // ---------------------------------------------------------
        List<DijkstraResult> results = dijkstraAllCandidates(graph, START_ID, END_ID, maxTransfers);

        // ---------------------------------------------------------
        // (6) 결과가 하나도 없으면 빈 결과 반환
        // ---------------------------------------------------------
        if (results.isEmpty()) {
            Map<String, Object> out = new HashMap<>();
            out.put("totalMinutes", 0);
            out.put("segments", Collections.emptyList());
            out.put("reason", "NO_PATH");
            out.put("requestedTransfers", maxTransfers);
            out.put("candidates", Collections.emptyList()); // 빈 후보 리스트
            return out;
        }

        // ---------------------------------------------------------
        // (7) 각 결과별 경로 복원 및 후보 리스트 생성
        // ---------------------------------------------------------
        List<Map<String, Object>> candidates = new ArrayList<>();

        for (DijkstraResult res : results) {
            List<Edge> edges = reconstructEdges(res, START_ID, END_ID);
            List<Map<String, Object>> segments = buildSegments(edges, stopPointMap);

            Map<String, Object> cand = new HashMap<>();
            cand.put("totalMinutes", res.totalMinutes);
            cand.put("segments", segments);

            int usedTransfers = (res.usedRides <= 0 ? 0 : Math.max(0, res.usedRides - 1));
            cand.put("usedTransfers", usedTransfers);
            cand.put("transferPenaltyMin", TRANSFER_PENALTY_MIN);
            cand.put("transferPenaltyTotalMinutes", usedTransfers * TRANSFER_PENALTY_MIN);

            candidates.add(cand);
        }

        // 후보들을 시간순(오름차순)으로 정렬 (가장 빠른게 0번 인덱스)
        candidates.sort(Comparator.comparingDouble(m -> (double) m.get("totalMinutes")));

        // 기본 응답 구조(하위 호환 및 편리성):
        // 최상위 필드에는 "Best Candidate(=시간 최소)"의 정보를 채운다.
        Map<String, Object> best = candidates.get(0);
        
        Map<String, Object> out = new HashMap<>();
        out.putAll(best); // totalMinutes, segments, usedTransfers 등 복사

        out.put("requestedTransfers", maxTransfers);
        out.put("candidates", candidates); // 전체 후보 리스트 포함

        return out;
    }

    // =========================
    // 유틸: 그래프 간선 추가
    // =========================
    private void addEdge(Map<String, List<Edge>> graph, String fromId, Edge edge) {
        graph.computeIfAbsent(fromId, k -> new ArrayList<>()).add(edge);
    }

    // =========================
    // 유틸: 형변환
    // =========================
    private String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private double asDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0.0;
        }
    }


    private int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    // =========================
    // 유틸: 도보 시간 및 거리 계산
    // =========================
    private double metersToWalkMinutes(double meters) {
        if (meters <= 0) return 0.0;
        double sec = meters / WALK_SPEED_MPS;
        return sec / 60.0;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0; // 지구 반지름 (m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    

// =========================
// 유틸: TRAM 노드ID("TRAM_244") → 정거장 이름
// =========================
private String getTramNameByNodeId(String nodeId) {
    if (nodeId == null) return null;
    if (!nodeId.startsWith("TRAM_")) return null;
    initTramData();
    for (TramStation ts : tramStations) {
        String id = "TRAM_" + ts.id;
        if (id.equals(nodeId)) return ts.name;
    }
    return null;
}

// =========================
// 유틸: BUS 노드ID("DJB800....") → 정류장 이름
// =========================
private String getBusStopNameByNodeId(String routeId, String nodeId) {
    if (routeId == null || routeId.isBlank()) return null;
    if (nodeId == null || nodeId.isBlank()) return null;

    try {
        Map<String, String> map = BUS_ROUTE_STOPNAME_CACHE.get(routeId);
        if (map == null) {
            map = fetchAndCacheRouteStopNameMap(routeId);
        }
        if (map == null) return null;
        return map.get(nodeId);
    } catch (Exception ignore) {
        return null;
    }
}

private Map<String, String> fetchAndCacheRouteStopNameMap(String routeId) {
    // 이미 누가 채웠을 수 있으므로, double-check 방식으로 최소 호출
    Map<String, String> cached = BUS_ROUTE_STOPNAME_CACHE.get(routeId);
    if (cached != null) return cached;

    try {
        String url = UriComponentsBuilder
                .fromHttpUrl("http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteAcctoThrghSttnList")
                .queryParam("serviceKey", TAGO_SERVICE_KEY)
                .queryParam("_type", "json")
                .queryParam("cityCode", TAGO_CITY_CODE)
                .queryParam("routeId", routeId)
                .queryParam("pageNo", "1")
                .queryParam("numOfRows", "300")
                .build(false)
                .toUriString();

        String body = busNameRestTemplate.getForObject(url, String.class);
        if (body == null || body.isBlank()) {
            BUS_ROUTE_STOPNAME_CACHE.put(routeId, Collections.emptyMap());
            return BUS_ROUTE_STOPNAME_CACHE.get(routeId);
        }

        JsonNode root = busNameObjectMapper.readTree(body);
        JsonNode itemsNode = root.path("response").path("body").path("items").path("item");

        Map<String, String> map = new HashMap<>();
        if (itemsNode.isArray()) {
            for (JsonNode it : itemsNode) {
                String nid = safeText(it, "nodeid");
                String nm = safeText(it, "nodenm");
                if (nid != null && !nid.isBlank() && nm != null && !nm.isBlank()) {
                    map.putIfAbsent(nid, nm);
                }
            }
        } else if (itemsNode.isObject()) {
            String nid = safeText(itemsNode, "nodeid");
            String nm = safeText(itemsNode, "nodenm");
            if (nid != null && !nid.isBlank() && nm != null && !nm.isBlank()) {
                map.putIfAbsent(nid, nm);
            }
        }

        BUS_ROUTE_STOPNAME_CACHE.put(routeId, map);
        return map;
    } catch (HttpStatusCodeException ex) {
        // 외부 API 오류(4xx/5xx)는 캐시를 비워둬서 동일 routeId에 대해 계속 폭발하지 않도록 막는다.
        BUS_ROUTE_STOPNAME_CACHE.put(routeId, Collections.emptyMap());
        return BUS_ROUTE_STOPNAME_CACHE.get(routeId);
    } catch (Exception ex) {
        BUS_ROUTE_STOPNAME_CACHE.put(routeId, Collections.emptyMap());
        return BUS_ROUTE_STOPNAME_CACHE.get(routeId);
    }
}

private String safeText(JsonNode node, String field) {
    if (node == null || field == null) return null;
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) return null;
    return v.asText(null);
}

// =========================
// 유틸: routeId -> routeNo 조회 (추가됨)
// =========================
private String getBusRouteNoByRouteId(String routeId) {
    if (routeId == null || routeId.isBlank()) return null;
    
    // 1. 캐시 확인
    if (BUS_ROUTE_NUMBER_CACHE.containsKey(routeId)) {
        return BUS_ROUTE_NUMBER_CACHE.get(routeId);
    }
    
    // 2. API 호출
    try {
        String url = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteInfoIem"
                + "?serviceKey=" + TAGO_SERVICE_KEY
                + "&_type=json"
                + "&cityCode=" + TAGO_CITY_CODE
                + "&routeId=" + routeId;
                
        String body = busNameRestTemplate.getForObject(url, String.class);
        JsonNode root = busNameObjectMapper.readTree(body);
        JsonNode item = root.path("response").path("body").path("items").path("item");
        
        String routeNo = safeText(item, "routeno");
        if (routeNo != null) {
            BUS_ROUTE_NUMBER_CACHE.put(routeId, routeNo);
            return routeNo;
        }
    } catch (Exception e) {
        // e.printStackTrace();
    }
    
    // 실패 시 routeId 반환 혹은 null
    BUS_ROUTE_NUMBER_CACHE.put(routeId, routeId); // 실패해도 캐시에 넣어 재시도 방지
    return routeId;
}
/**
 * 허용 환승 횟수(maxTransfers)를 "승차 횟수 상한(MAX_RIDES)"으로 변환한다.
 *
 * 규칙:
 * - 환승 0회 → 승차 1회 (직행만)
 * - 환승 N회 → 승차 N+1회
 * - 음수 입력 방지
 * - 상태 폭발 방지를 위해 상한 캡 적용
 */
private int clampMaxRides(int maxTransfers) {
    int safeTransfers = Math.max(0, maxTransfers); // 음수 방지
    // 요청한 "환승 횟수"를 그대로 "승차 횟수" 상한으로 변환한다.
    // - 환승 0회 → 승차 1회
    // - 환승 N회 → 승차 N+1회
    return safeTransfers + 1;
}
// ✅ 여기까지    // =========================
    // 다익스트라 알고리즘 (수정됨: 모든 환승 횟수별 최적 경로 탐색)
    // =========================
    private List<DijkstraResult> dijkstraAllCandidates(Map<String, List<Edge>> graph, String startId, String endId, int maxTransfers) {
        final int MAX_RIDES = clampMaxRides(maxTransfers);

        // stateKey -> dist (전체 상태 공간 최단거리)
        Map<String, Double> dist = new HashMap<>();
        // stateKey -> prevStateKey (경로 복원용)
        Map<String, String> prevState = new HashMap<>();
        // stateKey -> prevEdge (경로 복원용)
        Map<String, Edge> prevEdge = new HashMap<>();

        PriorityQueue<NodeDist> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a.dist));

        String startKey = stateKey(startId, "NONE", "", -1, 0);
        dist.put(startKey, 0.0);
        pq.add(new NodeDist(startKey, 0.0));

        // 각 승차 횟수(rideCount)별로 도착지(endId)에 도달한 최단 거리/상태를 기록
        // Index 0 사용 안함. rides=1(직행)..MAX_RIDES
        // bestEndDists[r] = 승차 r회로 도착했을 때의 최소 시간
        double[] bestEndDists = new double[MAX_RIDES + 1];
        String[] bestEndKeys = new String[MAX_RIDES + 1];
        for (int i = 0; i <= MAX_RIDES; i++) {
            bestEndDists[i] = Double.POSITIVE_INFINITY;
        }

        while (!pq.isEmpty()) {
            NodeDist cur = pq.poll();

            if (cur.dist > dist.getOrDefault(cur.stateKey, Double.POSITIVE_INFINITY)) {
                continue;
            }

            State curState = parseStateKey(cur.stateKey);

            // 도착지 도달 확인
            if (Objects.equals(curState.nodeId, endId)) {
                int rides = curState.rideCount;
                if (rides >= 1 && rides <= MAX_RIDES) {
                    // 해당 승차 횟수에서 더 빠른 길이면 업데이트
                    if (cur.dist < bestEndDists[rides]) {
                        bestEndDists[rides] = cur.dist;
                        bestEndKeys[rides] = cur.stateKey;
                    }
                }
                // 여기서 break하지 않고 계속 탐색 (다른 승차 횟수의 최적해도 찾아야 함)
            }

            List<Edge> neighbors = graph.getOrDefault(curState.nodeId, Collections.emptyList());
            for (Edge e : neighbors) {
                // (1) 승차 카운트 / 환승 페널티 계산
                int nextRides = curState.rideCount;
                double penalty = 0.0;

                if ("BUS".equals(e.mode) || "TRAM".equals(e.mode)) {
                    boolean isSameVehicle =
                        Objects.equals(curState.mode, e.mode) &&
                        Objects.equals(curState.routeId, e.routeId) &&
                        (!"BUS".equals(e.mode) || curState.updowncd == e.updowncd);

                    if (!isSameVehicle) {
                        // 새로 승차(또는 환승)
                        nextRides = curState.rideCount + 1;
                        if (curState.rideCount > 0) {
                            penalty = TRANSFER_PENALTY_MIN;
                        }
                    }
                }

                if (nextRides > MAX_RIDES) {
                    continue; // 상한 초과
                }

                // (2) 다음 상태 키 구성
                String nextMode = e.mode;
                String nextRoute = (e.routeId == null ? "" : e.routeId);
                if ("WALK".equals(nextMode)) {
                    nextMode = "WALK";
                    nextRoute = "";
                }
                int nextUpdown = ("BUS".equals(nextMode) ? e.updowncd : -1);
                String nextKey = stateKey(e.toId, nextMode, nextRoute, nextUpdown, nextRides);

                // (3) 거리(시간) 갱신
                double step = Math.max(0.0, e.minutes) + penalty;
                double newDist = cur.dist + step;

                if (newDist < dist.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
                    dist.put(nextKey, newDist);
                    prevState.put(nextKey, cur.stateKey);
                    prevEdge.put(nextKey, e);
                    pq.add(new NodeDist(nextKey, newDist));
                }
            }
        }

        // 결과 수집
        List<DijkstraResult> results = new ArrayList<>();
        for (int r = 1; r <= MAX_RIDES; r++) {
            if (bestEndKeys[r] != null) {
                // 승차 r회로 도착하는 경로가 존재함
                DijkstraResult res = new DijkstraResult();
                res.totalMinutes = bestEndDists[r];
                res.endStateKey = bestEndKeys[r];
                res.prevState = prevState;
                res.prevEdge = prevEdge;
                res.usedRides = r;
                results.add(res);
            }
        }
        return results;
    }

    // =========================
    // 경로 역추적 (End -> Start)
    // =========================
    private List<Edge> reconstructEdges(DijkstraResult result, String startId, String endId) {
        if (result == null || result.endStateKey == null) {
            return Collections.emptyList();
        }

        List<Edge> edges = new ArrayList<>();
        String curKey = result.endStateKey;

        // startKey는 dijkstra()에서 시작한 상태와 동일해야 함
        String startKey = stateKey(startId, "NONE", "", -1, 0);

        while (!Objects.equals(curKey, startKey)) {
            Edge e = result.prevEdge.get(curKey);
            String prevKey = result.prevState.get(curKey);

            if (e == null || prevKey == null) {
                // 역추적 불가(연결 끊김) → 빈 경로
                return Collections.emptyList();
            }

            edges.add(e);
            curKey = prevKey;
        }

        Collections.reverse(edges); // Start -> End 순서로 뒤집기
        return edges;
    }

    // =========================
    // 프론트엔드 응답용 Segment 빌드
    // =========================
    private List<Map<String, Object>> buildSegments(List<Edge> edges, Map<String, StopPoint> stopPointMap) {
        if (edges == null || edges.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> segments = new ArrayList<>();
        Map<String, Object> curSeg = null;
        String curMode = null;
        String curRouteId = null;
        Integer curUpdowncd = null;

        for (Edge e : edges) {
            boolean newSegment = (curSeg == null) 
                || !Objects.equals(curMode, e.mode)
                || ("BUS".equals(e.mode) && (!Objects.equals(curRouteId, e.routeId) || !Objects.equals(curUpdowncd, e.updowncd)));

            if (newSegment) {
                if (curSeg != null) segments.add(curSeg);

                curMode = e.mode;
                curRouteId = e.routeId;
                curUpdowncd = ("BUS".equals(e.mode) ? Integer.valueOf(e.updowncd) : null);

                curSeg = new HashMap<>();
                curSeg.put("mode", e.mode);
                curSeg.put("routeId", e.routeId);
                if ("BUS".equals(e.mode)) {
                    curSeg.put("updowncd", e.updowncd);
                    curSeg.put("routeNo", getBusRouteNoByRouteId(e.routeId)); // 노선번호 추가
                }
                curSeg.put("minutes", 0.0);
                curSeg.put("points", new ArrayList<double[]>());
                curSeg.put("nodeIds", new ArrayList<String>());
                curSeg.put("nodeNames", new ArrayList<String>());
}

            double oldMin = (double) curSeg.get("minutes");
            curSeg.put("minutes", oldMin + e.minutes);

            @SuppressWarnings("unchecked")
List<double[]> points = (List<double[]>) curSeg.get("points");

@SuppressWarnings("unchecked")
List<String> nodeIds = (List<String>) curSeg.get("nodeIds");

@SuppressWarnings("unchecked")
List<String> nodeNames = (List<String>) curSeg.get("nodeNames");

StopPoint from = stopPointMap.get(e.fromId);
StopPoint to = stopPointMap.get(e.toId);

if (from != null) {
    points.add(new double[]{from.lng, from.lat});
    nodeIds.add(e.fromId);
    if ("TRAM".equals(e.mode)) {
        nodeNames.add(getTramNameByNodeId(e.fromId));
    } else if ("BUS".equals(e.mode)) {
        nodeNames.add(getBusStopNameByNodeId(e.routeId, e.fromId));
    } else {
        nodeNames.add(null);
    }
}

if (to != null) {
    points.add(new double[]{to.lng, to.lat});
    nodeIds.add(e.toId);
    if ("TRAM".equals(e.mode)) {
        nodeNames.add(getTramNameByNodeId(e.toId));
    } else if ("BUS".equals(e.mode)) {
        nodeNames.add(getBusStopNameByNodeId(e.routeId, e.toId));
    } else {
        nodeNames.add(null);
    }
}}

        if (curSeg != null) segments.add(curSeg);

        return segments;
    }

    // =========================
    // 내부 클래스 정의
    // =========================
    private static class StopPoint {
        String id; double lat, lng;
        StopPoint(String id, double lat, double lng) { this.id=id; this.lat=lat; this.lng=lng; }
    }

    private static class Edge {
        String mode, routeId, fromId, toId;
        double minutes;
        int updowncd; // BUS 방향(0:상행, 1:하행), BUS가 아니면 -1

        Edge(String m, String r, String f, String t, double min, int updowncd) {
            this.mode = m;
            this.routeId = r;
            this.fromId = f;
            this.toId = t;
            this.minutes = min;
            this.updowncd = updowncd;
        }
    }

    private static class State {
        String nodeId;
        String mode;
        String routeId;
        int updowncd; // BUS 방향(0/1). BUS가 아니면 -1
        int rideCount;

        State(String nodeId, String mode, String routeId, int updowncd, int rideCount) {
            this.nodeId = nodeId;
            this.mode = mode;
            this.routeId = routeId;
            this.updowncd = updowncd;
            this.rideCount = rideCount;
        }
    }

    private String stateKey(String nodeId, String mode, String routeId, int updowncd, int rideCount) {
        return String.valueOf(nodeId) + "|" + String.valueOf(mode) + "|" + String.valueOf(routeId) + "|" + updowncd + "|" + rideCount;
    }

    private State parseStateKey(String key) {
        // key format: nodeId|mode|routeId|updowncd|rideCount
        if (key == null) return new State("", "NONE", "", -1, 0);

        String[] parts = key.split("\\|", -1);
        String nodeId = (parts.length > 0 ? parts[0] : "");
        String mode = (parts.length > 1 ? parts[1] : "NONE");
        String routeId = (parts.length > 2 ? parts[2] : "");
        int updowncd = -1;
        if (parts.length > 3) {
            try { updowncd = Integer.parseInt(parts[3]); } catch (Exception ignore) {}
        }
        int rideCount = 0;
        if (parts.length > 4) {
            try { rideCount = Integer.parseInt(parts[4]); } catch (Exception ignore) {}
        }
        return new State(nodeId, mode, routeId, updowncd, rideCount);
    }

    private static class NodeDist {
        String stateKey;
        double dist;

        NodeDist(String key, double d) {
            stateKey = key;
            dist = d;
        }
    }

    private static class DijkstraResult {
        double totalMinutes;
        String endStateKey;
        Map<String, String> prevState;
        Map<String, Edge> prevEdge;

        // 요청/결과 승차 횟수 정보 (환승 수는 rides-1)
        int requestedRides;
        int usedRides;
        boolean exactMatched;
    }
}

// 수정됨 끝