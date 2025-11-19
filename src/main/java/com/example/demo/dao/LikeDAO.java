package com.example.demo.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * LikeDAO: 좋아요 기능을 위한 DB 접근 클래스
 * 게시글/댓글 좋아요 정보 INSERT, DELETE, COUNT, EXISTS 등을 처리한다.
 */
@Repository
public class LikeDAO {

    @Autowired
    private DataSource dataSource;  //기존 코드 스타일 따라 DataSource 사용

    // DB 연결 메소드
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    //좋아요 여부 확인 (중복 방지용)
    public boolean exists(String targetType, int targetId, String userId) {
        String sql = "SELECT 1 FROM likes WHERE target_type=? AND target_id=? AND user_id=? LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, targetType);
            ps.setInt(2, targetId);
            ps.setString(3, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();  // 하나라도 있으면 이미 좋아요 누른 상태
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false; // 오류 시 false 처리
        }
    }

    //좋아요 추가 (INSERT)
    public int insert(String targetType, int targetId, String userId) {
        String sql = "INSERT INTO likes (target_type, target_id, user_id) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, targetType);
            ps.setInt(2, targetId);
            ps.setString(3, userId);

            return ps.executeUpdate(); // INSERT 성공 시 1 반환

        } catch (Exception e) {
            e.printStackTrace();
            return 0;  // 실패
        }
    }

    //좋아요 삭제 (DELETE)
    public int delete(String targetType, int targetId, String userId) {
        String sql = "DELETE FROM likes WHERE target_type=? AND target_id=? AND user_id=?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, targetType);
            ps.setInt(2, targetId);
            ps.setString(3, userId);

            return ps.executeUpdate(); // DELETE 성공 시 삭제된 ROW 수 반환

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    //좋아요 개수 조회
    public int count(String targetType, int targetId) {
        String sql = "SELECT COUNT(*) FROM likes WHERE target_type=? AND target_id=?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, targetType);
            ps.setInt(2, targetId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1); // COUNT 결과
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0; // 기본값
    }
}
