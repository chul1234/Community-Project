package com.example.demo.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserDAO {
    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // save, delete, insert 메소드들은 수정할 필요가 없습니다. (생략)
    public int save(Map<String, Object> user) {
        if (user.get("id_for_update") == null) {
            String sql = SqlLoader.getSql("user.insert");
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, (String) user.get("user_id"));
                pstmt.setString(2, (String) user.get("name"));
                pstmt.setString(3, (String) user.get("phone"));
                pstmt.setString(4, (String) user.get("email"));
                pstmt.setString(5, (String) user.get("password"));
                return pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); return 0; }
        } else {
            String sql = SqlLoader.getSql("user.update");
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, (String) user.get("name"));
                pstmt.setString(2, (String) user.get("phone"));
                pstmt.setString(3, (String) user.get("email"));
                pstmt.setString(4, (String) user.get("id_for_update"));
                return pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); return 0; }
        }
    }

    // [최종 수정] findByUserId 메소드: role_ids와 role_names를 모두 담도록 수정
    public Optional<Map<String, Object>> findByUserId(String userId) {
        String sql = SqlLoader.getSql("user.select.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("user_id", rs.getString("user_id"));
                    user.put("name", rs.getString("name"));
                    user.put("phone", rs.getString("phone"));
                    user.put("email", rs.getString("email"));
                    user.put("password", rs.getString("password"));
                    user.put("role_ids", rs.getString("role_ids"));
                    user.put("role_names", rs.getString("role_names"));
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // [최종 수정] findAll 메소드: 역할 ID와 역할 이름을 모두 담도록 수정
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> userList = new ArrayList<>();
        String sql = SqlLoader.getSql("user.select.all");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getString("user_id"));
                user.put("name", rs.getString("name"));
                user.put("phone", rs.getString("phone"));
                user.put("email", rs.getString("email"));
                user.put("password", rs.getString("password"));
                user.put("role_ids", rs.getString("role_ids"));       // 내부 로직용 역할 ID
                user.put("role_names", rs.getString("role_names")); // 화면 표시용 역할 이름
                userList.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userList;
    }

    // deleteByUserId, deleteUserRoles, insertUserRoles, insertUserRole 메소드는 수정할 필요가 없습니다.
    public int deleteByUserId(String userId) {
        String sql = SqlLoader.getSql("user.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }
    public void deleteUserRoles(String userId) throws SQLException {
        String sql = SqlLoader.getSql("user_roles.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        }
    }
    public void insertUserRoles(String userId, List<String> roleIds) throws SQLException {
        String sql = SqlLoader.getSql("user_roles.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (String roleId : roleIds) {
                pstmt.setString(1, userId);
                pstmt.setString(2, roleId);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }
    public void insertUserRole(String userId, String roleId) {
        String sql = SqlLoader.getSql("user_roles.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}