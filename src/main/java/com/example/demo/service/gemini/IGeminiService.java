package com.example.demo.service.gemini;

import java.util.List;
import java.util.Map;

public interface IGeminiService {
    // Legacy method for backward compatibility if needed, or update entirely
    Map<String, Object> getChatResponse(String message);
    
    // New method with history
    default Map<String, Object> getChatResponse(String message, List<Map<String, String>> history) {
        return getChatResponse(message);
    }
}
