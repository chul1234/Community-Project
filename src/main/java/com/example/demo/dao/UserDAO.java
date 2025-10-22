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

//@Repository: 이 클래스가 데이터베이스와 통신하는 부품(DAO)임
@Repository
public class UserDAO {
    // @Autowired: Spring이 미리 설정해 둔 데이터베이스 연결
    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    //저장 메소드 
    public int save(Map<String, Object> user) {
        // 만약 전달받은 user Map에 'id_for_update'라는 키가 없으면, 신규 생성(INSERT)
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
            //'user.update' 키에 해당하는 SQL 명령어
            String sql = SqlLoader.getSql("user.update");
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, (String) user.get("name"));
                pstmt.setString(2, (String) user.get("phone"));
                pstmt.setString(3, (String) user.get("email"));
                // 마지막 WHERE 조건절의 물음표(?)를 'id_for_update' 값
                pstmt.setString(4, (String) user.get("id_for_update"));
                // 완성된 SQL을 실행하고, 영향을 받은 행의 수를 반환
                return pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); return 0; }
        }
    }
    // 사용자의 로그인 ID(PK)를 이용해 특정 사용자 한 명의 정보를 조회
    public Optional<Map<String, Object>> findByUserId(String userId) {
        // 'user.select.by_user_id' 키에 해당하는 SQL 명령어
        String sql = SqlLoader.getSql("user.select.by_user_id");
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL의 물음표(?)에 파라미터로 받은 userId 값
            pstmt.setString(1, userId);
            // SQL을 실행하고, 그 결과를 ResultSet(rs)
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 조회된 사용자 정보를 담을 새로운 Map
                    Map<String, Object> user = new HashMap<>();
                    // 현재 행의 각 컬럼 값을 읽어와서, Map에 이름표(key)와 함께 저장
                    user.put("user_id", rs.getString("user_id"));
                    user.put("name", rs.getString("name"));
                    user.put("phone", rs.getString("phone"));
                    user.put("email", rs.getString("email"));
                    user.put("password", rs.getString("password"));
                    // SQL 쿼리의 별명(as)으로 지정한 'role_ids'와 'role_names' 값을 가져와 저장
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
                // SQL 쿼리의 별명(as)으로 지정한 'role_ids'와 'role_names' 값을 가져와 저장
                user.put("role_ids", rs.getString("role_ids"));
                user.put("role_names", rs.getString("role_names"));
                userList.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userList;
    }
//user_id를 이용해 사용자를 삭제
    public int deleteByUserId(String userId) {
        String sql = SqlLoader.getSql("user.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }
    
    // user_id를 이용해 users_roles 테이블에서 해당 사용자의 모든 역할 정보를 삭제
    public void deleteUserRoles(String userId) throws SQLException {
        String sql = SqlLoader.getSql("user_roles.delete.by_user_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        }
    }

    // user_id를 이용해 users_roles 테이블에서 해당 사용자의 모든 역할 정보
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

    // 한 명의 사용자에게 단 하나의 역할을 부여
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