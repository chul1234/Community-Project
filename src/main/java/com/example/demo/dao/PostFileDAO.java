package com.example.demo.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PostFileDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 파일 메타데이터 INSERT
     */
    public int insertFile(int postId, Map<String, Object> fileInfo) {
        String sql = SqlLoader.getSql("file.insert");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId);
            pstmt.setString(2, (String) fileInfo.get("original_name"));
            pstmt.setString(3, (String) fileInfo.get("saved_name"));
            pstmt.setString(4, (String) fileInfo.get("content_type"));
            pstmt.setLong(5, (Long) fileInfo.get("file_size"));

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 특정 게시글(postId)에 연결된 파일 목록 조회
     */
    public List<Map<String, Object>> findByPostId(int postId) {
        String sql = SqlLoader.getSql("file.select.by_post_id");
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> file = new HashMap<>();
                    file.put("file_id", rs.getInt("file_id"));
                    file.put("post_id", rs.getInt("post_id"));
                    file.put("original_name", rs.getString("original_name"));
                    file.put("saved_name", rs.getString("saved_name"));
                    file.put("content_type", rs.getString("content_type"));
                    file.put("file_size", rs.getLong("file_size"));
                    list.add(file);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    /**
     * 특정 파일 한 개 조회
     */
    public Optional<Map<String, Object>> findById(int fileId) {
        String sql = SqlLoader.getSql("file.select.by_id");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> file = new HashMap<>();
                    file.put("file_id", rs.getInt("file_id"));
                    file.put("post_id", rs.getInt("post_id"));
                    file.put("original_name", rs.getString("original_name"));
                    file.put("saved_name", rs.getString("saved_name"));
                    file.put("content_type", rs.getString("content_type"));
                    file.put("file_size", rs.getLong("file_size"));
                    return Optional.of(file);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * 특정 파일 한 개 삭제
     */
    public int deleteById(int fileId) {
        String sql = SqlLoader.getSql("file.delete.by_id");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, fileId);
            return pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 특정 게시글에 연결된 모든 파일 삭제
     */
    public int deleteByPostId(int postId) {
        String sql = SqlLoader.getSql("file.delete.by_post_id");

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
