// 수정됨: 대용량 게시판 검색 기능 + user_id 검색 시 인덱스 활용 (= 비교로 변경)

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
public class BigPostDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // SqlLoader 유틸을 사용하는 공통 메서드 (다른 DAO들과 동일 패턴)
    private String sql(String key) {
        return SqlLoader.getSql(key);
    }

    // ------------------------------------------------------
    // 1) 기존 OFFSET 방식 (page, size)로 조회할 때 사용 가능
    //    → 검색 조건이 없는 단순 버전: 내부적으로 검색 버전 호출
    // ------------------------------------------------------
    public List<Map<String, Object>> findAll(int size, int offset) {
        return findAll(size, offset, null, null);
    }

    // ------------------------------------------------------
    // 1-2) OFFSET + 검색 조건 버전
    //      searchType: title, content, title_content, user_id, time
    // ------------------------------------------------------
    public List<Map<String, Object>> findAll(int size, int offset, String searchType, String searchKeyword) {
        List<Map<String, Object>> list = new ArrayList<>();

        // 기본 SELECT
        StringBuilder sql = new StringBuilder(
                "SELECT post_id, title, user_id, created_at " +
                "FROM big_posts "
        );

        // WHERE 동적 생성
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            // searchType이 없으면 기본을 'title_content'로
            if (searchType == null || searchType.isEmpty()) {
                searchType = "title_content";
            }

            if ("title".equals(searchType)) {
                where.append(" AND title LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("content".equals(searchType)) {
                where.append(" AND content LIKE ? ");
                params.add("%" + searchKeyword + "%");
            } else if ("title_content".equals(searchType)) {
                where.append(" AND (title LIKE ? OR content LIKE ?) ");
                params.add("%" + searchKeyword + "%");
                params.add("%" + searchKeyword + "%");
            } else if ("user_id".equals(searchType)) {
                // 🔸 수정 포인트 1: user_id LIKE '%키워드%' → '=' 비교로 변경 (인덱스 활용)
                where.append(" AND user_id = ? ");
                params.add(searchKeyword);
            } else if ("time".equals(searchType)) {
                // 0~23 시 기준 (문자열 그대로 바인딩, DB에서 숫자로 변환됨)
                where.append(" AND HOUR(created_at) = ? ");
                params.add(searchKeyword);
            }
        }

        sql.append(where);
        sql.append(" ORDER BY post_id DESC ");
        sql.append(" LIMIT ? OFFSET ? ");

        params.add(size);
        params.add(offset);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("post_id", rs.getLong("post_id"));
                    row.put("title", rs.getString("title"));
                    row.put("user_id", rs.getString("user_id"));
                    row.put("created_at", rs.getTimestamp("created_at"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    // ------------------------------------------------------
    // 2) 전체 개수 조회
    //    - 검색어 없으면: 카운터 테이블 사용 (기존 로직)
    //    - 검색어 있으면: big_posts에서 조건 COUNT(*)
    // ------------------------------------------------------
    public int countAll() {
        return countAll(null, null);
    }

    public int countAll(String searchType, String searchKeyword) {
        // 검색어 없으면 기존 카운터 테이블 사용 (매우 빠름)
        if (searchKeyword == null || searchKeyword.isEmpty()) {
            String query = sql("bigpost.count.all"); // SELECT total_count FROM big_posts_counter WHERE id = 1

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    long total = rs.getLong(1);   // total_count
                    return (int) total;           // int로 캐스팅
                }
                return 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        }

        // 검색어가 있는 경우: big_posts 에서 조건 COUNT(*)
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM big_posts "
        );
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (searchType == null || searchType.isEmpty()) {
            searchType = "title_content";
        }

        if ("title".equals(searchType)) {
            where.append(" AND title LIKE ? ");
            params.add("%" + searchKeyword + "%");
        } else if ("content".equals(searchType)) {
            where.append(" AND content LIKE ? ");
            params.add("%" + searchKeyword + "%");
        } else if ("title_content".equals(searchType)) {
            where.append(" AND (title LIKE ? OR content LIKE ?) ");
            params.add("%" + searchKeyword + "%");
            params.add("%" + searchKeyword + "%");
        } else if ("user_id".equals(searchType)) {
            // 🔸 수정 포인트 2: COUNT 쿼리에서도 user_id = ? 로 통일 (인덱스 활용)
            where.append(" AND user_id = ? ");
            params.add(searchKeyword);
        } else if ("time".equals(searchType)) {
            where.append(" AND HOUR(created_at) = ? ");
            params.add(searchKeyword);
        }

        sql.append(where);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    // ------------------------------------------------------
    // 3) 키셋 페이징 - 첫 페이지
    // ------------------------------------------------------
    public List<Map<String, Object>> findFirstPage(int size) {
        String query = sql("bigpost.select.first");
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, size);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("post_id", rs.getLong("post_id"));
                    row.put("title", rs.getString("title"));
                    row.put("user_id", rs.getString("user_id"));
                    row.put("created_at", rs.getTimestamp("created_at"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    // ------------------------------------------------------
    // 4) 키셋 페이징 - 다음 페이지
    //    lastId 보다 작은 post_id 들 중에서 상위 size개
    // ------------------------------------------------------
    public List<Map<String, Object>> findNextPage(long lastId, int size) {
        String query = sql("bigpost.select.next");
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setLong(1, lastId);
            pstmt.setInt(2, size);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("post_id", rs.getLong("post_id"));
                    row.put("title", rs.getString("title"));
                    row.put("user_id", rs.getString("user_id"));
                    row.put("created_at", rs.getTimestamp("created_at"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    // ------------------------------------------------------
    // 5) 단건 조회 (일반 게시판 getPost 같은 역할)
    // ------------------------------------------------------
    public Optional<Map<String, Object>> findById(long postId) {
        String query = sql("bigpost.select.one");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setLong(1, postId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("post_id", rs.getLong("post_id"));
                    row.put("title", rs.getString("title"));
                    row.put("content", rs.getString("content"));
                    row.put("user_id", rs.getString("user_id"));
                    row.put("created_at", rs.getTimestamp("created_at"));
                    // big_posts 테이블 컬럼 더 있으면 여기서 추가로 put
                    return Optional.of(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    // ------------------------------------------------------
    // 6) INSERT + 카운터 +1  (일반 게시판의 save와 비슷한 역할)
    // ------------------------------------------------------
    public int insert(Map<String, Object> post) {
        String insertSql = sql("bigpost.insert");
        String incSql    = sql("bigpost.counter.increment");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // 트랜잭션 시작

            int affected = 0;

            // 6-1) big_posts INSERT
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, (String) post.get("title"));
                pstmt.setString(2, (String) post.get("content"));
                pstmt.setString(3, (String) post.get("user_id"));

                affected = pstmt.executeUpdate();

                // 생성된 PK(post_id) 되돌려받고 싶으면 여기서 처리
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        post.put("post_id", id);
                    }
                }
            }

            // 6-2) INSERT 성공 시 카운터 +1
            if (affected > 0) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(incSql)) {
                    pstmt2.executeUpdate();
                }
            }

            conn.commit();
            return affected;

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // ------------------------------------------------------
    // 7) UPDATE (제목/내용 수정, 카운터는 변경 없음)
    // ------------------------------------------------------
    public int update(Map<String, Object> post) {
        String updateSql = sql("bigpost.update");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(updateSql)) {

            pstmt.setString(1, (String) post.get("title"));
            pstmt.setString(2, (String) post.get("content"));
            pstmt.setLong(3, ((Number) post.get("post_id")).longValue());

            return pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // ------------------------------------------------------
    // 8) DELETE + 카운터 -1
    // ------------------------------------------------------
    public int delete(long postId) {
        String deleteSql = sql("bigpost.delete");
        String decSql    = sql("bigpost.counter.decrement");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            int affected = 0;

            // 8-1) big_posts 행 삭제
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                pstmt.setLong(1, postId);
                affected = pstmt.executeUpdate();
            }

            // 8-2) 삭제 성공 시 카운터 -1
            if (affected > 0) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(decSql)) {
                    pstmt2.executeUpdate();
                }
            }

            conn.commit();
            return affected;

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}

// 수정됨 끝
