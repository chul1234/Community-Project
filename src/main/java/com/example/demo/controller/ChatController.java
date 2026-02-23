package com.example.demo.controller;

import com.example.demo.service.gemini.IGeminiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final IGeminiService geminiService;

    public ChatController(IGeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, Object> payload) {
        String message = (String) payload.get("message");
        List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history");
        
        if (message == null || message.trim().isEmpty()) {
            return Map.of("text", "메시지를 입력해주세요.");
        }
        
        // Pass both message and history to the service
        return geminiService.getChatResponse(message, history);
    }
}
