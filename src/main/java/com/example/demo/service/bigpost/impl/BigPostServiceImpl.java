package com.example.demo.service.bigpost.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.dao.BigPostDAO;
import com.example.demo.service.bigpost.IBigPostService;

@Service
public class BigPostServiceImpl implements IBigPostService {

    @Autowired
    private BigPostDAO bigPostDAO;

    @Override
    public Map<String, Object> getBigPosts(int page, int size) {
        // 1. Offset 계산 (1페이지 -> 0, 2페이지 -> 20 ...)
        int offset = (page - 1) * size;

        // 2. DAO 호출
        List<Map<String, Object>> posts = bigPostDAO.findAll(size, offset);
        int totalItems = bigPostDAO.countAll();
        
        // 3. 총 페이지 수 계산
        int totalPages = (int) Math.ceil((double) totalItems / size);

        // 4. 결과 리턴
        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);
        result.put("totalItems", totalItems);
        result.put("totalPages", totalPages);
        result.put("currentPage", page);

        return result;
    }
}