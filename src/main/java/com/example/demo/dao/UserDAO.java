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

    /**
     * [수정] save 메소드는 이제 id가 아닌 user_id가 있는지 확인합니다.
     * 또한, user_id는 자동 생성이 아니므로 generatedKeys 로직을 제거합니다.
     */
    public int save(Map<String, Object> user) {
        // user_id가 없으면 INSERT, 있으면 UPDATE로 분기하는 로직은 유효하나,
        // 이 프로젝트에서는 생성과 수정이 명확히 분리되어 있으므로
        // 여기서는 INSERT 로직만 처리하는 것이 더 명확할 수 있습니다.
        // (단, UserEdit 기능이 user_id를 수정하지 않으므로 UPDATE 로직은 그대로 둡니다.)
        
        // user 객체에 "id" 키 대신 "user_id" 키가 있는지 확인해야 하지만,
        // 현재 로직은 신규 생성 시 id가 null인 점을 이용하므로 그대로 활용합니다.
        // 단, createUser, createUsers 서비스 로직에서 PK인 user_id를 반드시 넣어줘야 합니다.

        if (user.get("id_for_update") == null) { // 'id' 대신 명확한 키 사용 또는 다른 조건 사용
            // INSERT 로직
            String sql = SqlLoader.getSql("user.insert"); // user.insert 쿼리도 수정 필요
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, (String) user.get("user_id")); // PK인 user_id를 직접 설정
                pstmt.setString(2, (String) user.get("name"));
                pstmt.setString(3, (String) user.get("phone"));
                pstmt.setString(4, (String) user.get("email"));
                pstmt.setString(5, (String) user.get("password"));
                
                return pstmt.executeUpdate();
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
                pstmt.setString(4, (String) user.get("id_for_update")); // WHERE 조건절에 user_id 사용
                return pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        }
    }

    /**
     * [수정] PK인 user_id(String)로 사용자를 조회합니다.
     */
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
                    user.put("role_name", rs.getString("role_name"));
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // 모든 사용자 조회 (내부 로직은 GROUP_CONCAT으로 이미 잘 되어 있음)
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
                user.put("role_name", rs.getString("role_name"));
                userList.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userList;
    }

    /**
     * [수정] PK인 user_id(String)로 사용자를 삭제합니다.
     */
    public int deleteByUserId(String userId) {
        String sql = SqlLoader.getSql("user.delete.by_user_id"); // 쿼리 키 변경 필요
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * [수정] user_id(String)를 기준으로 역할을 삭제합니다.
     */
    public void deleteUserRoles(String userId) throws SQLException {
        String sql = SqlLoader.getSql("user_roles.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        }
    }

    /**
     * [수정] user_id(String)와 role_id(String) 목록을 기준으로 역할을 추가합니다.
     */
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

    /**
     * [수정] user_id(String)와 role_id(String)를 기준으로 단일 역할을 추가합니다.
     */
    public void insertUserRole(String userId, String roleId) {
        String sql = SqlLoader.getSql("user_roles.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}