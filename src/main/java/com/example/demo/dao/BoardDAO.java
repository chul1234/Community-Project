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
import java.util.Optional;

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

    /**
     * posts 테이블에 새로운 게시글을 저장합니다.
     * @param post 저장할 게시글 정보 (Map)
     * @return 영향을 받은 행의 수 (성공 시 1)
     */
    public int save(Map<String, Object> post) {
        String sql = SqlLoader.getSql("post.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, (String) post.get("title"));
            pstmt.setString(2, (String) post.get("content"));
            pstmt.setString(3, (String) post.get("user_id"));

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * post_id로 특정 게시글 하나를 조회합니다.
     * @param postId 조회할 게시글의 ID
     * @return 게시글 정보 (Optional<Map>)
     */
    public Optional<Map<String, Object>> findById(int postId) {
        String sql = SqlLoader.getSql("post.select.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId); // SQL의 ? 부분에 postId 값을 채웁니다.

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> post = new HashMap<>();
                    post.put("post_id", rs.getInt("post_id"));
                    post.put("title", rs.getString("title"));
                    post.put("content", rs.getString("content"));
                    post.put("user_id", rs.getString("user_id"));
                    post.put("author_name", rs.getString("author_name"));
                    post.put("created_at", rs.getTimestamp("created_at"));
                    return Optional.of(post); // 찾은 데이터를 Optional로 감싸서 반환
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty(); // 데이터를 찾지 못하면 빈 Optional 반환
    }

    /**
     * post_id로 특정 게시글의 제목과 내용을 수정합니다.
     */
    public int update(Map<String, Object> post) {
        String sql = SqlLoader.getSql("post.update");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, (String) post.get("title"));
            pstmt.setString(2, (String) post.get("content"));
            pstmt.setInt(3, (Integer) post.get("post_id"));

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * post_id로 특정 게시글을 삭제합니다.
     */
    public int delete(int postId) {
        String sql = SqlLoader.getSql("post.delete.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}