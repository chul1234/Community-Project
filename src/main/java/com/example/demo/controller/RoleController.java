package com.example.demo.controller;

import com.example.demo.dao.RoleDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class RoleController {

    @Autowired
    private RoleDAO roleDAO;

    /**
     * 프론트엔드에 모든 역할 목록을 반환하는 API
     * 주소: /api/roles
     */
    @GetMapping("/api/roles")
    public List<Map<String, Object>> getAllRoles() {
        return roleDAO.findAll();
    }
}