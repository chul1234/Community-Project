package com.example.demo.service.menu; // (새 패키지)

import java.util.List;
import java.util.Map;

public interface IMenuService {
    /**
     * 메뉴를 계층 구조(대메뉴 + 하위 소메뉴 리스트)로 조회합니다.
     * @return 계층화된 메뉴 목록
     */
    List<Map<String, Object>> getGroupedMenus();
}