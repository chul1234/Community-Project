package com.example.demo.service.gemini;

import java.util.Map;

public interface IGeminiService {
    Map<String, Object> getChatResponse(String message);
}
