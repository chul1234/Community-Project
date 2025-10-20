package com.example.demo.controller;

import com.example.demo.dao.BoardDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class BoardController {

    @Autowired
    private BoardDAO boardDAO;

    /**
     * 프론트엔드에 모든 게시글 목록을 반환하는 API
     * 주소: /api/posts
     */
    @GetMapping("/api/posts")
    public List<Map<String, Object>> getAllPosts() {
        return boardDAO.findAll();
    }
}