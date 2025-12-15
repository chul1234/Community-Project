// 수정됨: 대용량(big_posts) 통계 추가 + 기존(posts) 통계 유지
//       - GET /api/stats/posts       : 기존 일반 게시판 통계
//       - GET /api/stats/big-posts   : 대용량 게시판 통계 추가

package com.example.demo.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class StatsDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    // ----------------------------------
    // 일반 게시판 통계
    // ----------------------------------
    public Map<String, Object> getPostStats() {
        Map<String, Object> result = new HashMap<>();

        // 사용자별 게시글 수 Top10
        result.put("topUsers", query(
            "SELECT user_id,COUNT(*) cnt FROM posts GROUP BY user_id ORDER BY cnt DESC LIMIT 10"
        ));

        // 조회수 Top10
        result.put("topViews", query(
            "SELECT title,view_count FROM posts ORDER BY view_count DESC LIMIT 10"
        ));

        // 날짜별 게시글 수 (전 기간)
        result.put("daily", query(
            "SELECT DATE(created_at) day,COUNT(*) cnt FROM posts GROUP BY DATE(created_at) ORDER BY day"
        ));

        return result;
    }

    // ----------------------------------
    // 대용량 게시판 통계 (big_posts)
    // ----------------------------------
    public Map<String, Object> getBigPostStats() {
        Map<String, Object> result = new HashMap<>();

        // 사용자별 게시글 수 Top10
        result.put("topUsers", query(
            "SELECT user_id,COUNT(*) cnt FROM big_posts GROUP BY user_id ORDER BY cnt DESC LIMIT 10"
        ));

        // 조회수 Top10
        result.put("topViews", query(
            "SELECT post_id,title,view_count FROM big_posts ORDER BY view_count DESC LIMIT 10"
        ));

        // 날짜별 게시글 수 (전 기간)
        result.put("daily", query(
            "SELECT DATE(created_at) day,COUNT(*) cnt FROM big_posts GROUP BY DATE(created_at) ORDER BY day"
        ));

        return result;
    }

    // ----------------------------------
    // 공통 ResultSet → List<Map>
    // ----------------------------------
    private List<Map<String, Object>> query(String sql) {
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int cols = rs.getMetaData().getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                list.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

}
// 수정됨 끝
