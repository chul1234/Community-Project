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
public class RoleDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * roles 테이블의 모든 역할 목록을 조회합니다.
     * @return 역할 목록 (List of Maps)
     */
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> roleList = new ArrayList<>();
        String sql = "SELECT * FROM roles";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> role = new HashMap<>();
                // [수정] role_id는 VARCHAR(문자열)이므로 getString으로 읽어야 합니다.
                role.put("role_id", rs.getString("role_id"));
                role.put("role_name", rs.getString("role_name"));
                roleList.add(role);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleList;
    }
}