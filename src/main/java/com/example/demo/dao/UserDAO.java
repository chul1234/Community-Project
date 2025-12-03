package com.example.demo.dao;

// [] java.sql.Types 임포트 (countAll 수정 시 필요할 수 있음)
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList; // [] (혹시 몰라 추가, 이 케이스엔 불필요)
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class UserDAO {
    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // [유지] save 메소드 (수정 없음)
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

    // [유지] findByUserId 메소드 (수정 없음)
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

    // ▼▼▼ [수정] findAll 메소드 (System.out.println 제거됨) ▼▼▼
    public List<Map<String, Object>> findAll(int limit, int offset, String searchType, String searchKeyword) {
        List<Map<String, Object>> userList = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder(
            "SELECT u.*, GROUP_CONCAT(r.role_id SEPARATOR ', ') as role_ids, " +
            "GROUP_CONCAT(r.role_name SEPARATOR ', ') as role_names " +
            "FROM users u " +
            "LEFT JOIN users_roles ur ON u.user_id = ur.user_id " +
            "LEFT JOIN roles r ON ur.role_id = r.role_id"
        );
        
        List<Object> params = new ArrayList<>();
        StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");

        // [수정됨] 디버깅용 System.out.println 제거함

        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            // [수정됨] 디버깅용 System.out.println 제거함
            if ("user_id".equals(searchType)) {
                whereSql.append(" AND u.user_id LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("name".equals(searchType)) {
                whereSql.append(" AND u.name LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("email".equals(searchType)) {
                whereSql.append(" AND u.email LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("phone".equals(searchType)) {
                whereSql.append(" AND u.phone LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("role_name".equals(searchType)) {
                whereSql.append(" AND r.role_name LIKE ? ");
                params.add("%" + searchKeyword + "%");
            }
        }

        sql.append(whereSql);
        sql.append(" GROUP BY u.user_id LIMIT ? OFFSET ? ");
        params.add(limit);
        params.add(offset);
        
        // [수정됨] 디버깅용 System.out.println 제거함

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) { 

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("user_id", rs.getString("user_id"));
                    user.put("name", rs.getString("name"));
                    user.put("phone", rs.getString("phone"));
                    user.put("email", rs.getString("email"));
                    user.put("password", rs.getString("password"));
                    user.put("role_ids", rs.getString("role_ids"));
                    user.put("role_names", rs.getString("role_names"));
                    userList.add(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // [수정됨] 디버깅용 System.out.println 제거함
        return userList;
    } 

    // ▼▼▼ [수정] countAll 메소드 (System.out.println 제거됨) ▼▼▼
    public int countAll(String searchType, String searchKeyword) {
        
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(DISTINCT u.user_id) " +
            "FROM users u " +
            "LEFT JOIN users_roles ur ON u.user_id = ur.user_id " +
            "LEFT JOIN roles r ON ur.role_id = r.role_id"
        );

        List<Object> params = new ArrayList<>();
        StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");

        // [수정됨] 디버깅용 System.out.println 제거함

        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            // [수정됨] 디버깅용 System.out.println 제거함
            if ("user_id".equals(searchType)) {
                whereSql.append(" AND u.user_id LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("name".equals(searchType)) {
                whereSql.append(" AND u.name LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("email".equals(searchType)) {
                whereSql.append(" AND u.email LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("phone".equals(searchType)) {
                whereSql.append(" AND u.phone LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("role_name".equals(searchType)) {
                whereSql.append(" AND r.role_name LIKE ? ");
                params.add("%" + searchKeyword + "%");
            }
        }
        
        sql.append(whereSql);

        // [수정됨] 디버깅용 System.out.println 제거함

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt(1); 
                    // [수정됨] 디버깅용 System.out.println 제거함
                    return total;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // [유지] 삭제 메소드 (수정 없음)
    public int deleteByUserId(String userId) {
        String sql = SqlLoader.getSql("user.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }
    
    // [유지] 역할 삭제 메소드 (수정 없음)
    public void deleteUserRoles(String userId) throws SQLException {
        String sql = SqlLoader.getSql("user_roles.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        }
    }

    // [유지] 역할 삽입 (여러 개) 메소드 (수정 없음)
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

    // [유지] 역할 삽입 (한 개) 메소드 (수정 없음)
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