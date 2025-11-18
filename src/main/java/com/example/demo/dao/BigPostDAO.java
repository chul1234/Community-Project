package com.example.demo.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class BigPostDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // 목록 조회
    public List<Map<String, Object>> findAll(int limit, int offset) {
        // 작성하신 SQL 키: bigpost.select.page = ... LIMIT ?, ?
        String sql = SqlLoader.getSql("bigpost.select.page"); 
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // [핵심] SQL이 "LIMIT ?, ?" (쉼표) 형태이므로 순서가 중요합니다.
            // MySQL 문법: LIMIT 시작위치(offset), 개수(limit)
            pstmt.setInt(1, offset); // 1번째 물음표: 시작 위치 (0, 20, 40...)
            pstmt.setInt(2, limit);  // 2번째 물음표: 가져올 개수 (20)
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("post_id", rs.getLong("post_id"));
                    map.put("title", rs.getString("title"));
                    map.put("user_id", rs.getString("user_id"));
                    map.put("created_at", rs.getTimestamp("created_at"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 전체 개수 조회
    public int countAll() {
        // 작성하신 SQL 키: bigpost.select.count
        String sql = SqlLoader.getSql("bigpost.select.count");
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}