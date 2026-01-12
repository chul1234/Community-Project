package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.bmk.IBmkService;

@RestController
public class BmkController {

    @Autowired
    private IBmkService bmkService;

    // 즐겨찾기 토글
    @PostMapping("/api/bmk/toggle")
    public Map<String, Object> toggle(
            @RequestParam("type") String targetType,
            @RequestParam("id") String targetId,
            @RequestParam("alias") String alias,
            @RequestParam("userId") String userId
    ) {
        boolean liked = bmkService.toggleBookmark(userId, targetType, targetId, alias);
        
        Map<String, Object> res = new HashMap<>();
        res.put("liked", liked);
        return res;
    }

    // 즐겨찾기 상태 확인
    @GetMapping("/api/bmk/check")
    public Map<String, Object> check(
            @RequestParam("type") String targetType,
            @RequestParam("id") String targetId,
            @RequestParam("userId") String userId
    ) {
        boolean liked = bmkService.checkBookmark(userId, targetType, targetId);
        
        Map<String, Object> res = new HashMap<>();
        res.put("liked", liked);
        return res;
    }

    // 내 즐겨찾기 목록
    @GetMapping("/api/bmk/list")
    public List<Map<String, Object>> list(@RequestParam("userId") String userId) {
        return bmkService.getMyBookmarks(userId);
    }
}
