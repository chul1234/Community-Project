package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.bigpost.IBigPostService;

@RestController
public class BigPostController {

    @Autowired
    private IBigPostService bigPostService;

    // ----------------------------------------------------
    // ① 기존 OFFSET 방식 (기능 유지) 
    // ----------------------------------------------------
    @GetMapping("/api/big-posts")
    public Map<String, Object> getBigPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size  // 기본값 20
    ) {
        return bigPostService.getBigPosts(page, size);
    }

    // ----------------------------------------------------
    // ② 초고속 키셋 페이징: 첫 페이지 (OFFSET 없음)
    // ----------------------------------------------------
    @GetMapping("/api/big-posts/first")
    public List<Map<String, Object>> getFirstPage(
            @RequestParam(defaultValue = "20") int size
    ) {
        return bigPostService.getFirstPage(size);
    }

    // ----------------------------------------------------
    // ③ 초고속 키셋 페이징: 다음 페이지 (post_id < lastId)
    // ----------------------------------------------------
    @GetMapping("/api/big-posts/next")
    public List<Map<String, Object>> getNextPage(
            @RequestParam long lastId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return bigPostService.getNextPage(lastId, size);
    }
}
