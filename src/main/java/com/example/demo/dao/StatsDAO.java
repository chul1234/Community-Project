// 수정됨: 대용량(big_posts) 통계를 2개 통계 테이블(big_posts_user_stats, big_posts_daily_stats)에서 조회하도록 추가

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

        result.put("topUsers", query(
            "SELECT user_id,COUNT(*) cnt FROM posts GROUP BY user_id ORDER BY cnt DESC LIMIT 10"
        ));

        result.put("topViews", query(
            "SELECT title,view_count FROM posts ORDER BY view_count DESC LIMIT 10"
        ));

        result.put("daily", query(
            "SELECT DATE(created_at) day,COUNT(*) cnt FROM posts GROUP BY DATE(created_at) ORDER BY day"
        ));

        return result;
    }

    // ----------------------------------
    // 대용량 게시판 통계 (2개 테이블 기반)
    // ----------------------------------
    public Map<String, Object> getBigPostStats() {
        Map<String, Object> result = new HashMap<>();

        // 사용자별 게시글 수 Top10 (통계 테이블)
        result.put("topUsers", query(
            "SELECT user_id, cnt FROM big_posts_user_stats ORDER BY cnt DESC LIMIT 10"
        ));

        // 조회수 Top10 (원본 big_posts, view_count 인덱스 활용)
        // ※ 여기만은 통계테이블로 분리하지 않아도 인덱스로 빨라야 정상
        result.put("topViews", query(
            "SELECT title, view_count FROM big_posts ORDER BY view_count DESC LIMIT 10"
        ));

        // 일별 게시글 수(전체기간) (통계 테이블)
        result.put("daily", query(
            "SELECT day, cnt FROM big_posts_daily_stats ORDER BY day"
        ));

        return result;
    }

    // ----------------------------------
    // (선택) 통계 테이블 리빌드 (테스트용)
    // ----------------------------------
    public void rebuildBigPostStats() {
        execute("TRUNCATE TABLE big_posts_user_stats");
        execute(
            "INSERT INTO big_posts_user_stats (user_id, cnt) " +
            "SELECT user_id, COUNT(*) AS cnt FROM big_posts GROUP BY user_id"
        );

        execute("TRUNCATE TABLE big_posts_daily_stats");
        execute(
            "INSERT INTO big_posts_daily_stats (day, cnt) " +
            "SELECT DATE(created_at) AS day, COUNT(*) AS cnt " +
            "FROM big_posts GROUP BY DATE(created_at) ORDER BY day"
        );
    }

    private void execute(String sql) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
