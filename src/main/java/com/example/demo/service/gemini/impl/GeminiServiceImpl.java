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
import java.util.Map;

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
    private static final String TAGO_SERVICE_KEY = "ff623cef3aa0e011104003d8973105076b9f4ce098a93e4b6de36a9f2560529c";
    private static final String CITY_CODE = "25";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IPathService pathService;

    @Override
    public Map<String, Object> getChatResponse(String message) {
        try {
            // 1. Construct Request with Tools
            ObjectNode requestBody = objectMapper.createObjectNode();
            
            // System Instruction
            ObjectNode systemInstruction = requestBody.putObject("systemInstruction");
            systemInstruction.putObject("parts").put("text", "You are a helpful Bus Assistant for Daejeon City. " +
                    "Use 'get_bus_station' to find stops by name. " +
                    "Use 'get_shortest_path' to find routes between coordinates. " +
                    "If a path is found, summarize the route briefly (e.g. 'Take bus 108 at ...'). " +
                    "Always answer in Korean.");

            // Tools definition
            ArrayNode tools = requestBody.putArray("tools");
            ArrayNode functionDeclarations = tools.addObject().putArray("function_declarations");
            
            // Tool 1: get_bus_station
            ObjectNode tool1 = functionDeclarations.addObject();
            tool1.put("name", "get_bus_station");
            tool1.put("description", "Search for bus stations by name (e.g., 'City Hall', 'Hanbit Tower'). Returns stations with IDs and coordinates.");
            ObjectNode params1 = tool1.putObject("parameters");
            params1.put("type", "OBJECT");
            ObjectNode props1 = params1.putObject("properties");
            props1.putObject("keyword").put("type", "STRING").put("description", "Station name query");
            params1.putArray("required").add("keyword");

            // Tool 2: get_shortest_path
            ObjectNode tool2 = functionDeclarations.addObject();
            tool2.put("name", "get_shortest_path");
            tool2.put("description", "Calculate shortest path between two coordinates.");
            ObjectNode params2 = tool2.putObject("parameters");
            params2.put("type", "OBJECT");
            ObjectNode props2 = params2.putObject("properties");
            props2.putObject("fromLat").put("type", "NUMBER");
            props2.putObject("fromLng").put("type", "NUMBER");
            props2.putObject("toLat").put("type", "NUMBER");
            props2.putObject("toLng").put("type", "NUMBER");
            ArrayNode req2 = params2.putArray("required");
            req2.add("fromLat").add("fromLng").add("toLat").add("toLng");

            // User Content
            ArrayNode contents = requestBody.putArray("contents");
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
                } else if ("get_shortest_path".equals(funcName)) {
                    double fromLat = args.path("fromLat").asDouble();
                    double fromLng = args.path("fromLng").asDouble();
                    double toLat = args.path("toLat").asDouble();
                    double toLng = args.path("toLng").asDouble();
                    toolResult = pathService.solve(fromLat, fromLng, toLat, toLng, 500, 2);
                    
                    if (toolResult instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> casted = (Map<String, Object>) toolResult;
                        pathData = casted;
                    }
                }

                // 4. Send Tool Output back (Second Turn)
                ObjectNode nextRequest = objectMapper.createObjectNode();
                nextRequest.set("systemInstruction", systemInstruction); // Include system instruction again? safer to include or not? v1beta usually stateless but chat usually maintains. But REST needs history.
                // Actually system instruction should be persistent if we send full history? 
                // Let's just include tools again and history.
                
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
                funcResp.set("response", objectMapper.valueToTree(Map.of("content", toolResult))); // Wrap in content object
                
                // Call again
                JsonNode finalResponse = restTemplate.postForObject(url, nextRequest, JsonNode.class);
                
                String finalText = "죄송합니다. 처리 중 오류가 발생했습니다.";
                if (finalResponse != null && finalResponse.has("candidates") && !finalResponse.path("candidates").isEmpty()) {
                   JsonNode finalPart = finalResponse.path("candidates").get(0).path("content").path("parts").get(0);
                   if (finalPart.has("text")) {
                       finalText = finalPart.get("text").asText();
                   }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("text", finalText);
                if (pathData != null) {
                    result.put("path", pathData);
                }
                return result;

            } else {
                // Just text
                String text = part.path("text").asText();
                return Map.of("text", text);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("text", "오류가 발생했습니다: " + e.getMessage());
        }
    }

    private Object searchBusStations(String keyword) {
        try {
            // Using existing Open API logic (mimic BusApiController)
            String baseUrl = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnNoList";

            java.net.URI uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl) // Better than fromHttpUrl
                    .queryParam("serviceKey", TAGO_SERVICE_KEY)
                    .queryParam("_type", "json")
                    .queryParam("cityCode", CITY_CODE)
                    .queryParam("nodeNm", keyword)
                    .queryParam("numOfRows", 5)
                    .build()
                    .encode() // Encode the components (UTF-8)
                    .toUri();
            
            System.out.println("Calling Bus API: " + uri);

            String response = restTemplate.getForObject(uri, String.class);
            
            System.out.println("Bus API Response: " + response);

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
}
