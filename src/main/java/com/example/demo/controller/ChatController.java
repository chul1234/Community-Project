package com.example.demo.controller;

import com.example.demo.service.gemini.IGeminiService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final IGeminiService geminiService;

    public ChatController(IGeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Map.of("text", "메시지를 입력해주세요.");
        }
        return geminiService.getChatResponse(message);
    }
}
