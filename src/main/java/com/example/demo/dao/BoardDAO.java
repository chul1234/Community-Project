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
public class BoardDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * posts 테이블의 모든 게시글 목록을 조회합니다.
     * @return 게시글 목록 (List of Maps)
     */
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> postList = new ArrayList<>();
        String sql = SqlLoader.getSql("post.select.all");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> post = new HashMap<>();
                post.put("post_id", rs.getInt("post_id"));
                post.put("title", rs.getString("title"));
                post.put("user_id", rs.getString("user_id"));
                post.put("author_name", rs.getString("author_name")); // 작성자 이름 추가
                post.put("created_at", rs.getTimestamp("created_at"));
                postList.add(post);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return postList;
    }
}