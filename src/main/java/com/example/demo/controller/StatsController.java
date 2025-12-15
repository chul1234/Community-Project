// 수정됨: 대용량(big_posts) 통계 API 추가 (GET /api/stats/big-posts)

package com.example.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dao.StatsDAO;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsDAO statsDAO;

    // 일반 게시판 통계
    @GetMapping("/posts")
    public Map<String, Object> getPostStats() {
        return statsDAO.getPostStats();
    }

    // 대용량 게시판 통계
    @GetMapping("/big-posts")
    public Map<String, Object> getBigPostStats() {
        return statsDAO.getBigPostStats();
    }
}
// 수정됨 끝
