// 수정됨: /api/stats/big-posts + (선택) /api/stats/big-posts/rebuild 추가

package com.example.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    // (선택) 통계 테이블 재생성(테스트용)
    @PostMapping("/big-posts/rebuild")
    public String rebuildBigPostStats() {
        statsDAO.rebuildBigPostStats();
        return "OK";
    }
}
// 수정됨 끝
