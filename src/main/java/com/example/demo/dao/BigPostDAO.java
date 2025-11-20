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

    // 목록 조회 (기존 OFFSET 기반 페이징)
    public List<Map<String, Object>> findAll(int limit, int offset) {
        // 작성하 SQL 키: bigpost.select.page = ... LIMIT ?, ?
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
        // 작성하 SQL 키: bigpost.select.count
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

    // ----------------------------------------------------
    // ▼▼▼ 키셋 페이징용 메소드 추가 (post_id 기준) ▼▼▼
    // ----------------------------------------------------

    // 첫 페이지: 가장 최신글부터 limit 개수
    public List<Map<String, Object>> findFirstPage(int limit) {  // 수정됨
        String sql = SqlLoader.getSql("bigpost.select.first");   // 수정됨
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);  // LIMIT ?

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

    // 다음 페이지: 마지막으로 본 post_id 보다 작은 것들 중에서 최신부터 limit 개수
    public List<Map<String, Object>> findNextPage(long lastPostId, int limit) {  // 수정됨
        String sql = SqlLoader.getSql("bigpost.select.next");                   // 수정됨
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, lastPostId);  // WHERE post_id < ?
            pstmt.setInt(2, limit);        // LIMIT ?

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
}
