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
        // roles 테이블의 모든 데이터를 조회하는 간단한 SQL 쿼리입니다.
        String sql = "SELECT * FROM roles";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> role = new HashMap<>();
                role.put("role_id", rs.getInt("role_id"));
                role.put("role_name", rs.getString("role_name"));
                roleList.add(role);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleList;
    }
}