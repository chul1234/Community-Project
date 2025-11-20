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

    // --------------------------------------------
    // 기존 OFFSET 페이징 방식
    // --------------------------------------------
    @Override
    public Map<String, Object> getBigPosts(int page, int size) {
        int offset = (page - 1) * size;

        List<Map<String, Object>> posts = bigPostDAO.findAll(size, offset);
        int totalItems = bigPostDAO.countAll();

        int totalPages = (int) Math.ceil((double) totalItems / size);

        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);
        result.put("totalItems", totalItems);
        result.put("totalPages", totalPages);
        result.put("currentPage", page);

        return result;
    }

    // ---------------------------------------------------------
    // ▼▼▼ 초고속 키셋 페이징 방식 (OFFSET 없음) ▼▼▼  // 수정됨
    // ---------------------------------------------------------

    @Override
    public List<Map<String, Object>> getFirstPage(int size) {   // 수정됨
        return bigPostDAO.findFirstPage(size);  // 수정됨
    }

    @Override
    public List<Map<String, Object>> getNextPage(long lastId, int size) {  // 수정됨
        return bigPostDAO.findNextPage(lastId, size);  // 수정됨
    }
}
