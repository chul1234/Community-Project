package com.example.demo.controller;

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

    // 대용량 게시판 API 엔드포인트
    @GetMapping("/api/big-posts")
    public Map<String, Object> getBigPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size // 기본 20개씩 보기
    ) {
        return bigPostService.getBigPosts(page, size);
    }
}