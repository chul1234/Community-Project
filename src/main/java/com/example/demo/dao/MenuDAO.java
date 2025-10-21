package com.example.demo.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MenuDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * menus 테이블의 모든 메뉴 목록을 정렬하여 조회합니다.
     * (depth 오름차순, priority 오름차순)
     * @return 메뉴 목록 (List of Maps)
     */
    public List<Map<String, Object>> findAllSorted() {
        // sql.properties에서 정의한 SQL 키를 사용합니다.
        String sql = SqlLoader.getSql("menu.select.all_sorted");
        
        List<Map<String, Object>> menuList = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> menu = new HashMap<>();
                menu.put("menu_id", rs.getString("menu_id"));
                menu.put("parent_id", rs.getString("parent_id"));
                menu.put("menu_name", rs.getString("menu_name"));
                menu.put("depth", rs.getInt("depth"));
                menu.put("priority", rs.getInt("priority"));
                menu.put("path", rs.getString("path"));
                menu.put("template_url", rs.getString("template_url"));
                menuList.add(menu);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return menuList;
    }
}