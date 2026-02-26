package com.example.demo.dao;

import com.example.demo.entity.Comment;
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
    public List<Comment> findByPostId(int postId) {
        List<Comment> commentList = new ArrayList<>();
        String sql = SqlLoader.getSql("comment.select.by_post_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, postId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Comment comment = new Comment();
                    comment.setCommentId(rs.getInt("comment_id"));
                    comment.setPostId(rs.getInt("post_id"));
                    comment.setContent(rs.getString("content"));
                    comment.setUserId(rs.getString("user_id"));
                    comment.setAuthorName(rs.getString("author_name"));
                    
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        comment.setCreatedAt(createdAt.toLocalDateTime());
                    }
                    
                    comment.setParentCommentId(rs.getObject("parent_comment_id", Integer.class));
                    commentList.add(comment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commentList;
    }

    // 특정 ID의 댓글 하나 조회
    public Optional<Comment> findById(int commentId) {
        String sql = SqlLoader.getSql("comment.select.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, commentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Comment comment = new Comment();
                    comment.setCommentId(rs.getInt("comment_id"));
                    comment.setPostId(rs.getInt("post_id"));
                    comment.setContent(rs.getString("content"));
                    comment.setUserId(rs.getString("user_id"));
                    
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        comment.setCreatedAt(createdAt.toLocalDateTime());
                    }
                    
                    comment.setParentCommentId(rs.getObject("parent_comment_id", Integer.class));
                    return Optional.of(comment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    // 댓글 생성
    public int save(Comment comment) { 
        String sql = SqlLoader.getSql("comment.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, comment.getPostId());
            pstmt.setString(2, comment.getContent());
            pstmt.setString(3, comment.getUserId());

            Integer parentId = comment.getParentCommentId();
            if (parentId != null) {
                pstmt.setInt(4, parentId);
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 댓글 수정
    public int update(Comment comment) { 
        String sql = SqlLoader.getSql("comment.update");
        try (Connection conn = getConnection(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, comment.getContent());
            pstmt.setInt(2, comment.getCommentId());
            
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