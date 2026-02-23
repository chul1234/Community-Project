package com.example.demo.service.gemini.impl;

import com.example.demo.service.gemini.IGeminiService;
import com.example.demo.service.path.IPathService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;



import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class GeminiServiceImpl implements IGeminiService {

    @Value("${google.gemini.key}")
    private String apiKey;

    // Use gemini-pro (v1beta) or gemini-2.0-flash-exp. 
    // Using gemini-pro for stability as usually available, but user mentioned flash-exp in my previous thought.
    // Let's stick to the URL I used before but be careful.
    // The previous code used "gemini-2.0-flash-exp". I will keep it if it was working or intended.
    // Actually, `gemini-pro` is safer for general availability. 
    // However, function calling is better in newer models.
    // Let's use `gemini-1.5-flash` or `gemini-pro`. `gemini-pro` (v1beta) supports function calling.
    // I I will use `gemini-1.5-flash` as it is faster and cheaper/free tier friendly.
    // Wait, the previous code had `gemini-2.0-flash-exp`. I will stick to what I wrote before to avoid changing too many variables,
    // unless I want to be safe. `gemini-1.5-flash-latest` is a good bet.
    // Let's check what I wrote in the previous step... I wrote `gemini-2.0-flash-exp`.
    // I will use `gemini-1.5-flash` which is stable.
    // Use gemini-2.5-flash (confirmed available via ListModels)
    // Use gemini-2.5-flash-lite for better cost/latency as requested
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";
    
    // Service Key for Open API
    @Value("${tago.service.key}")
    private String tagoServiceKey;

    private static final String CITY_CODE = "25";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IPathService pathService;

    @Override
    public Map<String, Object> getChatResponse(String message) {
        return getChatResponse(message, null);
    }

    @Override
    public Map<String, Object> getChatResponse(String message, List<Map<String, String>> history) {
        try {
            // 1. Construct Request with Tools
            ObjectNode requestBody = objectMapper.createObjectNode();
            
            // System Instruction
            ObjectNode systemInstruction = requestBody.putObject("systemInstruction");
            systemInstruction.putObject("parts").put("text", "You are a helpful Bus Assistant for Daejeon City. " +
                    "If the user asks for what features you have or 'what can you do', list these 4 features: " +
                    "1. Real-time Bus Arrival (e.g., 'When does bus 102 come to Complex Terminal?'), " +
                    "2. Route Search (e.g., 'Way to KAIST from City Hall'), " +
                    "3. Station Location (e.g., 'Where is Hannam Univ stop?'), " +
                    "4. Station Routes (e.g., 'What buses go to Galleria?'). " +
                    "If the user asks for a route between two places (A to B), use 'search_route'. " +
                    "If the user asks for bus arrival time or 'what buses come to [Station]', use 'get_bus_arrival'. " +
                    "If the user asks for a list of all bus routes passing through a station (static list), use 'get_station_routes'. " +
                    "For simple station search, use 'get_bus_station'. " +
                    "Always answer in Korean. " +
                    "If the user input is not related to bus services (e.g. daily conversation, jokes, small talk), respond naturally and witty. " +
                    "If a path is found, summarize the route briefly.");

            // Tools definition
            ArrayNode tools = requestBody.putArray("tools");
            ArrayNode functionDeclarations = tools.addObject().putArray("function_declarations");
            
            // Tool 1: get_bus_station
            ObjectNode tool1 = functionDeclarations.addObject();
            tool1.put("name", "get_bus_station");
            tool1.put("description", "Search for bus stations by name (e.g., 'City Hall'). Returns stations with IDs and coordinates.");
            ObjectNode params1 = tool1.putObject("parameters");
            params1.put("type", "OBJECT");
            ObjectNode props1 = params1.putObject("properties");
            props1.putObject("keyword").put("type", "STRING").put("description", "Station name query");
            params1.putArray("required").add("keyword");

            // Tool 2: search_route
            ObjectNode tool2 = functionDeclarations.addObject();
            tool2.put("name", "search_route");
            tool2.put("description", "Search for a route between two places. User provides start and end names.");
            ObjectNode params2 = tool2.putObject("parameters");
            params2.put("type", "OBJECT");
            ObjectNode props2 = params2.putObject("properties");
            props2.putObject("startKeyword").put("type", "STRING");
            props2.putObject("endKeyword").put("type", "STRING");
            ArrayNode req2 = params2.putArray("required");
            req2.add("startKeyword").add("endKeyword");

            // Tool 3: get_bus_arrival
            ObjectNode tool3 = functionDeclarations.addObject();
            tool3.put("name", "get_bus_arrival");
            tool3.put("description", "Get real-time bus arrival information for a specific bus station.");
            ObjectNode params3 = tool3.putObject("parameters");
            params3.put("type", "OBJECT");
            ObjectNode props3 = params3.putObject("properties");
            props3.putObject("keyword").put("type", "STRING");
            params3.putArray("required").add("keyword");
            
            // Tool 4: get_station_routes (New)
            ObjectNode tool4 = functionDeclarations.addObject();
            tool4.put("name", "get_station_routes");
            tool4.put("description", "Get a comprehensive list of all bus routes that pass through a specific station.");
            ObjectNode params4 = tool4.putObject("parameters");
            params4.put("type", "OBJECT");
            ObjectNode props4 = params4.putObject("properties");
            props4.putObject("keyword").put("type", "STRING");
            params4.putArray("required").add("keyword");

            // User Content
            ArrayNode contents = requestBody.putArray("contents");
            
            // Append History if it exists
            if (history != null && !history.isEmpty()) {
                for (Map<String, String> h : history) {
                    ObjectNode turn = contents.addObject();
                    turn.put("role", h.get("role"));
                    turn.putObject("parts").put("text", h.get("text"));
                }
            }

            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            userContent.putObject("parts").put("text", message);

            // 2. Call Gemini API (First Turn)
            String url = GEMINI_URL + "?key=" + apiKey;
            JsonNode response = restTemplate.postForObject(url, requestBody, JsonNode.class);

            // 3. Process Response
            if (response == null || !response.has("candidates") || response.path("candidates").isEmpty()) {
                return Map.of("text", "죄송합니다. 응답을 받을 수 없습니다.");
            }

            JsonNode candidate = response.path("candidates").get(0);
            JsonNode content = candidate.path("content");
            if (!content.has("parts") || content.path("parts").isEmpty()) {
                 // Safety block or empty
                 return Map.of("text", "죄송합니다. 답변을 생성할 수 없습니다. (보안 정책 등)");
            }
            JsonNode part = content.path("parts").get(0);

            // Check if it's a function call
            if (part.has("functionCall")) {
                JsonNode functionCall = part.get("functionCall");
                String funcName = functionCall.get("name").asText();
                JsonNode args = functionCall.get("args");

                Object toolResult = null;
                Map<String, Object> pathData = null;

                if ("get_bus_station".equals(funcName)) {
                    String keyword = args.path("keyword").asText();
                    toolResult = searchBusStations(keyword);
                } else if ("search_route".equals(funcName)) {
                    String startKeyword = args.path("startKeyword").asText();
                    String endKeyword = args.path("endKeyword").asText();
                    toolResult = processRouteSearch(startKeyword, endKeyword);
                    
                    if (toolResult instanceof Map) {
                        Map<?,?> map = (Map<?,?>) toolResult;
                        if (map.containsKey("totalMinutes")) { 
                            pathData = (Map<String, Object>) toolResult;
                            // LLM에게 줄 요약 정보 생성 (할루시네이션 방지)
                            toolResult = formatRouteSummary(pathData);
                        }
                    }
                } else if ("get_bus_arrival".equals(funcName)) {
                    String keyword = args.path("keyword").asText();
                    toolResult = processBusArrival(keyword);
                } else if ("get_station_routes".equals(funcName)) {
                    String keyword = args.path("keyword").asText();
                    toolResult = processStationRoutes(keyword);
                } else {
                    // Unknown function handling: Feed back to the model so it can respond
                    toolResult = "The function '" + funcName + "' is not supported. Please answer the user's request naturally using your general knowledge, or apologize if you can't.";
                }

                // 4. Send Tool Output back (Second Turn)
                ObjectNode nextRequest = objectMapper.createObjectNode();
                // It's safer NOT to re-send system instruction in the second turn for v1beta if not needed, 
                // but preserving history is key.
                // Let's keep system instruction.
                nextRequest.set("systemInstruction", systemInstruction); 
                
                nextRequest.set("tools", requestBody.get("tools"));
                
                ArrayNode nextContents = nextRequest.putArray("contents");
                nextContents.add(userContent); // History 1
                
                ObjectNode modelTurn = nextContents.addObject();
                modelTurn.put("role", "model");
                modelTurn.set("parts", objectMapper.createArrayNode().add(part)); // History 2
                
                ObjectNode functionResponseTurn = nextContents.addObject();
                functionResponseTurn.put("role", "function");
                ObjectNode funcRespPart = functionResponseTurn.putArray("parts").addObject();
                ObjectNode funcResp = funcRespPart.putObject("functionResponse");
                funcResp.put("name", funcName);
                funcResp.set("response", objectMapper.valueToTree(Map.of("content", toolResult))); 
                
                // Call again
                JsonNode finalResponse = restTemplate.postForObject(url, nextRequest, JsonNode.class);
                
                String finalText = "죄송합니다. 처리 중 오류가 발생했습니다.";
                if (finalResponse != null && finalResponse.has("candidates") && !finalResponse.path("candidates").isEmpty()) {
                   JsonNode finalContent = finalResponse.path("candidates").get(0).path("content");
                   if (finalContent.has("parts") && !finalContent.path("parts").isEmpty()) {
                       JsonNode finalPart = finalContent.path("parts").get(0);
                       if (finalPart.has("text")) {
                           finalText = finalPart.get("text").asText();
                       }
                   }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("text", finalText);
                if (pathData != null) {
                    result.put("path", pathData);
                }
                
                // [New] Display Station on Map
                if (("get_bus_arrival".equals(funcName) || "get_bus_station".equals(funcName)) && toolResult != null) {
                    JsonNode firstStation = null;
                    
                    if (toolResult instanceof ArrayNode) {
                         ArrayNode arr = (ArrayNode) toolResult;
                         if (arr.size() > 0) {
                             if ("get_bus_arrival".equals(funcName)) {
                                 // arrival returns grouped object with station info
                                 firstStation = arr.get(0);
                             } else {
                                 // get_bus_station returns raw item list
                                 firstStation = arr.get(0);
                             }
                         }
                    } else if (toolResult instanceof JsonNode) {
                        firstStation = (JsonNode) toolResult;
                    }

                    if (firstStation != null) {
                        // Extract common fields (tago api field names)
                        // Arrival Group: stationId, stationName, gpsLat, gpsLng
                        // Station Item: nodeid, nodenm, gpslati, gpslong
                        
                        String sid = firstStation.has("stationId") ? firstStation.get("stationId").asText() : firstStation.path("nodeid").asText();
                        String snm = firstStation.has("stationName") ? firstStation.get("stationName").asText() : firstStation.path("nodenm").asText();
                        double lat = firstStation.has("gpsLat") ? firstStation.get("gpsLat").asDouble() : firstStation.path("gpslati").asDouble();
                        double lng = firstStation.has("gpsLng") ? firstStation.get("gpsLng").asDouble() : firstStation.path("gpslong").asDouble();
                        
                        // Check validity
                        if (sid != null && !sid.isEmpty() && lat > 0 && lng > 0) {
                             Map<String, Object> stationData = new HashMap<>();
                             stationData.put("id", sid);
                             stationData.put("name", snm);
                             stationData.put("lat", lat);
                             stationData.put("lng", lng);
                             result.put("station", stationData);
                        }
                    }
                }
                
                return result;

            } else {
                // Just text
                String text = part.path("text").asText();
                return Map.of("text", text);
            }

        } catch (HttpClientErrorException e) {
            // 429 Too Many Requests Handling
            if (e.getStatusCode().value() == 429) {
                return Map.of("text", "죄송합니다. 현재 질문량이 너무 많아 잠시 쉬고 있습니다. 🥲\n잠시 후(약 1분 뒤) 다시 질문해주세요!");
            }
            e.printStackTrace();
            return Map.of("text", "오류가 발생했습니다: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("text", "오류가 발생했습니다: " + e.getMessage());
        }
    }

    private Object searchBusStations(String keyword) {
        try {
            String baseUrl = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnNoList";

            java.net.URI uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("serviceKey", tagoServiceKey)
                    .queryParam("_type", "json")
                    .queryParam("cityCode", CITY_CODE)
                    .queryParam("nodeNm", keyword)
                    .queryParam("numOfRows", 5)
                    .build()
                    .encode() 
                    .toUri();
            
            System.out.println("Calling Bus API: " + uri);

            String response = restTemplate.getForObject(uri, String.class);
            // System.out.println("Bus API Response: " + response);

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("response").path("body").path("items").path("item");
            
            if (items.isMissingNode() || (items.isArray() && items.isEmpty())) {
                 return Collections.emptyList();
            }
            return items;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private Object processRouteSearch(String startKeyword, String endKeyword) {
        // 1. Find Start
        JsonNode startNodes = (JsonNode) getJsonNodeFromSearch(startKeyword);
        if (startNodes == null) return "출발지 '" + startKeyword + "'를 찾을 수 없습니다.";

        // 2. Find End
        JsonNode endNodes = (JsonNode) getJsonNodeFromSearch(endKeyword);
        if (endNodes == null) return "도착지 '" + endKeyword + "'를 찾을 수 없습니다.";

        // Pick 1st
        JsonNode start = startNodes.isArray() ? startNodes.get(0) : startNodes;
        JsonNode end = endNodes.isArray() ? endNodes.get(0) : endNodes;

        double startLat = start.path("gpslati").asDouble();
        double startLng = start.path("gpslong").asDouble();
        double endLat = end.path("gpslati").asDouble();
        double endLng = end.path("gpslong").asDouble();
        
        // 3. Solve Path
        return pathService.solve(startLat, startLng, endLat, endLng, 500, 2);
    }
    
    private Object processBusArrival(String keyword) {
        // 1. Find Station
        JsonNode nodes = (JsonNode) getJsonNodeFromSearch(keyword);
        if (nodes == null) return "정류장 '" + keyword + "'를 찾을 수 없습니다.";
        
        List<JsonNode> stationList = new java.util.ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode n : nodes) stationList.add(n);
        } else {
            stationList.add(nodes);
        }
        
        // 상위 3개 정류장만 조회 (API 호출 최소화)
        int limit = Math.min(stationList.size(), 3);
        ArrayNode totalResult = objectMapper.createArrayNode();
        boolean foundAny = false;
        
        for (int i = 0; i < limit; i++) {
            JsonNode station = stationList.get(i);
            String nodeId = station.path("nodeid").asText();
            String nodeNm = station.path("nodenm").asText();
            
            try {
                String baseUrl = "http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList";
                java.net.URI uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl)
                        .queryParam("serviceKey", tagoServiceKey)
                        .queryParam("_type", "json")
                        .queryParam("cityCode", CITY_CODE)
                        .queryParam("nodeId", nodeId)
                        .queryParam("numOfRows", 10)
                        .build().encode().toUri();
                        
                String response = restTemplate.getForObject(uri, String.class);
                JsonNode root = objectMapper.readTree(response);
                JsonNode items = root.path("response").path("body").path("items").path("item");
                
                if (!items.isMissingNode()) {
                    foundAny = true;
                    // 결과 묶음 객체
                    ObjectNode stationGroup = totalResult.addObject();
                    // 이름과 ID를 분리해서 저장 (프론트엔드 좌표 전달용)
                    stationGroup.put("stationName", nodeNm);
                    stationGroup.put("stationId", nodeId);
                    // 좌표 추가 (Search 결과인 stationList에서 가져옴)
                    if (station.has("gpslati") && station.has("gpslong")) {
                        stationGroup.put("gpsLat", station.get("gpslati").asDouble());
                        stationGroup.put("gpsLng", station.get("gpslong").asDouble());
                    }
                    
                    stationGroup.put("displayName", nodeNm + " (" + nodeId + ")");
                    ArrayNode arrivalList = stationGroup.putArray("arrivals");
                    
                    if (items.isArray()) {
                        for (JsonNode item : items) {
                            addArrivalSummary(arrivalList, item);
                        }
                    } else {
                        addArrivalSummary(arrivalList, items);
                    }
                }
                
            } catch (Exception e) {
                // 특정 정류장 조회 실패해도 계속 진행
            }
        }
        
        if (!foundAny) return "검색된 정류장(" + limit + "개)에 대한 도착 정보가 없습니다.";
        return totalResult;
    }
    
    private void addArrivalSummary(ArrayNode list, JsonNode item) {
        ObjectNode s = list.addObject();
        s.put("busNo", item.path("routeno").asText());
        s.put("eta", item.path("arrtime").asInt() / 60 + "분 후");
        s.put("remains", item.path("arrprevstationcnt").asInt() + "정거장 전");
    }

    private JsonNode getJsonNodeFromSearch(String keyword) {
        Object res = searchBusStations(keyword);
        if (res instanceof JsonNode) {
            return (JsonNode) res;
        }
        return null; // Empty list or other error
    }
    private Object processStationRoutes(String keyword) {
        // 1. Find Station
        JsonNode nodes = (JsonNode) getJsonNodeFromSearch(keyword);
        if (nodes == null) return "정류장 '" + keyword + "'를 찾을 수 없습니다.";
        
        JsonNode station = nodes.isArray() ? nodes.get(0) : nodes;
        String nodeId = station.path("nodeid").asText();
        String nodeNm = station.path("nodenm").asText();
        
        // 2. Call Through Route API
        try {
            String baseUrl = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnThrghRouteList";
            java.net.URI uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("serviceKey", tagoServiceKey)
                    .queryParam("_type", "json")
                    .queryParam("cityCode", CITY_CODE)
                    .queryParam("nodeId", nodeId)
                    .queryParam("numOfRows", 50)
                    .build().encode().toUri();
                    
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("response").path("body").path("items").path("item");
            
            if (items.isMissingNode()) return nodeNm + " 정류장을 경유하는 노선 정보가 없습니다.";
            
            // Collect route numbers
            java.util.List<String> routeNames = new java.util.ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    routeNames.add(item.path("routeno").asText() + "번");
                }
            } else {
                 routeNames.add(items.path("routeno").asText() + "번");
            }
            
            return Map.of("station", nodeNm, "routes", routeNames);
            
        } catch (Exception e) {
            e.printStackTrace();
            return "경유 노선 정보를 가져오는 중 오류 발생.";
        }
    }
    private String formatRouteSummary(Map<String, Object> pathData) {
        try {
            StringBuilder sb = new StringBuilder();
            double totalMin = (double) pathData.get("totalMinutes");
            sb.append("총 소요시간: 약 ").append((int)totalMin).append("분. ");
            
            List<Map<String, Object>> segments = (List<Map<String, Object>>) pathData.get("segments");
            if (segments == null || segments.isEmpty()) return "경로를 찾을 수 없습니다.";
            
            for (Map<String, Object> seg : segments) {
                String mode = (String) seg.get("mode");
                if ("BUS".equals(mode)) {
                    String routeNo = (String) seg.get("routeNo");
                    sb.append("[").append(routeNo).append("번 버스]로 이동, ");
                } else if ("TRAM".equals(mode)) {
                    // Start/End nodes might be TRAM stations, but '2호선' is fixed in PathService
                    sb.append("[트램(예정)]으로 이동, ");
                } else if ("WALK".equals(mode)) {
                    // sb.append("도보 이동, "); // 도보는 생략하거나 간단히
                }
            }
            sb.append("도착.");
            return sb.toString();
        } catch (Exception e) {
            return "경로 정보 처리 중 오류";
        }
    }
}
