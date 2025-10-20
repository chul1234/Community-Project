package com.example.demo.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Repository
public class CommentDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // 특정 게시글의 모든 댓글 조회
    public List<Map<String, Object>> findByPostId(int postId) {
        List<Map<String, Object>> commentList = new ArrayList<>();
        String sql = SqlLoader.getSql("comment.select.by_post_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> comment = new HashMap<>();
                    comment.put("comment_id", rs.getInt("comment_id"));
                    comment.put("post_id", rs.getInt("post_id"));
                    comment.put("content", rs.getString("content"));
                    comment.put("user_id", rs.getString("user_id"));
                    comment.put("author_name", rs.getString("author_name"));
                    comment.put("created_at", rs.getTimestamp("created_at"));
                    commentList.add(comment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commentList;
    }

    // 특정 ID의 댓글 하나 조회
    public Optional<Map<String, Object>> findById(int commentId) {
        String sql = SqlLoader.getSql("comment.select.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, commentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> comment = new HashMap<>();
                    comment.put("comment_id", rs.getInt("comment_id"));
                    comment.put("post_id", rs.getInt("post_id"));
                    comment.put("content", rs.getString("content"));
                    comment.put("user_id", rs.getString("user_id"));
                    comment.put("created_at", rs.getTimestamp("created_at"));
                    return Optional.of(comment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    // 댓글 생성
    public int save(Map<String, Object> comment) {
        String sql = SqlLoader.getSql("comment.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, (Integer) comment.get("post_id"));
            pstmt.setString(2, (String) comment.get("content"));
            pstmt.setString(3, (String) comment.get("user_id"));
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 댓글 수정
    public int update(Map<String, Object> comment) {
        String sql = SqlLoader.getSql("comment.update");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, (String) comment.get("content"));
            pstmt.setInt(2, (Integer) comment.get("comment_id"));
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 댓글 삭제
    public int delete(int commentId) {
        String sql = SqlLoader.getSql("comment.delete.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, commentId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}