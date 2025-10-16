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

    public int save(Map<String, Object> user) {
        if (user.get("id") == null || (Integer) user.get("id") == 0) {
            // INSERT 로직
            String sql = SqlLoader.getSql("user.insert");
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                pstmt.setString(1, (String) user.get("name"));
                pstmt.setString(2, (String) user.get("phone"));
                pstmt.setString(3, (String) user.get("email"));
                pstmt.setString(4, (String) user.get("user_id"));
                pstmt.setString(5, (String) user.get("password"));
                pstmt.setInt(6, (Integer) user.get("role_id"));
                
                int affectedRows = pstmt.executeUpdate();

                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            user.put("id", generatedKeys.getInt(1));
                        }
                    }
                }
                return affectedRows;
            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        } else {
            // UPDATE 로직
            String sql = SqlLoader.getSql("user.update");
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, (String) user.get("name"));
                pstmt.setString(2, (String) user.get("phone"));
                pstmt.setString(3, (String) user.get("email"));
                pstmt.setInt(4, (Integer) user.get("id"));
                return pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        }
    }

    // 로그인 ID(user_id)로 사용자 조회 (로그인 기능용)
    public Optional<Map<String, Object>> findByUserId(String userId) {
        String sql = SqlLoader.getSql("user.select.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("id"));
                    user.put("user_id", rs.getString("user_id"));
                    user.put("name", rs.getString("name"));
                    user.put("phone", rs.getString("phone"));
                    user.put("email", rs.getString("email"));
                    user.put("password", rs.getString("password"));
                    user.put("role_id", rs.getInt("role_id"));
                    user.put("role_name", rs.getString("role_name")); // 역할 이름 추가
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    // PK(id)로 사용자 조회
    public Optional<Map<String, Object>> findById(Integer id) {
        String sql = SqlLoader.getSql("user.select.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("id"));
                    user.put("user_id", rs.getString("user_id"));
                    user.put("name", rs.getString("name"));
                    user.put("phone", rs.getString("phone"));
                    user.put("email", rs.getString("email"));
                    user.put("password", rs.getString("password"));
                    user.put("role_id", rs.getInt("role_id"));
                    user.put("role_name", rs.getString("role_name")); // 역할 이름 추가
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // 모든 사용자 조회
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> userList = new ArrayList<>();
        String sql = SqlLoader.getSql("user.select.all");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("user_id", rs.getString("user_id"));
                user.put("name", rs.getString("name"));
                user.put("phone", rs.getString("phone"));
                user.put("email", rs.getString("email"));
                user.put("password", rs.getString("password"));
                user.put("role_id", rs.getInt("role_id"));
                user.put("role_name", rs.getString("role_name")); // 역할 이름 추가
                userList.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userList;
    }

    // PK(id)로 사용자 삭제
    public int deleteById(Integer id) {
        String sql = SqlLoader.getSql("user.delete.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 특정 사용자의 role_id를 변경합니다.
     * @param userId 권한을 변경할 사용자의 PK(id)
     * @param roleId 새로 부여할 역할의 ID(role_id)
     * @return 영향을 받은 행의 수 (성공 시 1)
     */
    public int updateRole(Integer userId, Integer roleId) {
        // sql.properties에 추가할 새로운 쿼리를 사용합니다.
        String sql = SqlLoader.getSql("user.update.role");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, roleId);
            pstmt.setInt(2, userId);

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}