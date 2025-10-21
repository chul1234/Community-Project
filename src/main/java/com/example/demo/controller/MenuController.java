package com.example.demo.controller;

import com.example.demo.service.menu.IMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MenuController {

    @Autowired
    private IMenuService menuService; // Service 계층 주입

    /**
     * 프론트엔드에 계층 구조로 그룹화된 메뉴 목록을 반환하는 API
     * 주소: /api/menus
     */
    @GetMapping("/api/menus")
    public List<Map<String, Object>> getMenus() {
        // Service를 통해 계층 구조로 가공된 메뉴 데이터를 가져와 반환
        return menuService.getGroupedMenus();
    }
}