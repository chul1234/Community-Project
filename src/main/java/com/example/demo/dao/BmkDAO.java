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
public class BmkDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private String sql(String key) {
        return SqlLoader.getSql(key);
    }

    // 즐겨찾기 추가 (Alias 포함)
    public int insert(String userId, String targetType, String targetId, String alias) {
        String query = sql("bmk.insert");
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, userId);
            ps.setString(2, targetType);
            ps.setString(3, targetId);
            ps.setString(4, alias);
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 즐겨찾기 삭제
    public int delete(String userId, String targetType, String targetId) {
        String query = sql("bmk.delete");
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, userId);
            ps.setString(2, targetType);
            ps.setString(3, targetId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 즐겨찾기 여부 확인
    public boolean exists(String userId, String targetType, String targetId) {
        String query = sql("bmk.exists");
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, userId);
            ps.setString(2, targetType);
            ps.setString(3, targetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 내 즐겨찾기 목록 조회
    public List<Map<String, Object>> selectList(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String query = sql("bmk.select.list");
        
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("user_id", rs.getString("user_id"));
                    row.put("target_type", rs.getString("target_type"));
                    row.put("target_id", rs.getString("target_id"));
                    row.put("alias", rs.getString("alias")); // Alias 추가
                    row.put("created_at", rs.getTimestamp("created_at"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
