package com.example.demo.dao;

import com.example.demo.dao.SqlLoader; // sql.properties 로더 import
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 사용
import org.springframework.stereotype.Repository; // @Repository 사용

import javax.sql.DataSource; // DataSource 사용 (DB 연결 풀)
import java.sql.*; // Connection, PreparedStatement, ResultSet 사용 (JDBC API)
import java.util.ArrayList; // ArrayList 사용 (List 구현체)
import java.util.HashMap; // HashMap 사용 (Map 구현체)
import java.util.List; // List 인터페이스 사용
import java.util.Map; // Map 인터페이스 사용
import java.util.Optional; // Optional 사용 (null 방지)

@Repository // @Repository: 데이터 접근 계층(DAO) 컴포넌트 선언
public class BoardDAO {

    @Autowired // @Autowired: Spring이 DataSource Bean을 자동 주입
    private DataSource dataSource;

    // getConnection(): 데이터 소스에서 DB Connection 객체 획득 메소드
    private Connection getConnection() throws SQLException {
        // dataSource.getConnection() 호출
        return dataSource.getConnection();
    }

    /**
     * posts 테이블의 모든 게시글 목록 조회 메소드
     * @return 게시글 목록 (List<Map<String, Object>>)
     */
    public List<Map<String, Object>> findAll() {
        // 결과 담을 ArrayList 생성
        List<Map<String, Object>> postList = new ArrayList<>();
        // SqlLoader.getSql() 호출하여 SQL 로드 ('pinned_order' 없는 SQL 사용)
        String sql = SqlLoader.getSql("post.select.all");

        // try-with-resources: conn, pstmt, rs 자동 자원 해제 보장
        try (Connection conn = getConnection(); // getConnection() 호출
             PreparedStatement pstmt = conn.prepareStatement(sql); // SQL 준비
             ResultSet rs = pstmt.executeQuery()) { // SQL 실행 및 결과(ResultSet) 받음

            // rs.next(): 결과 집합(ResultSet) 순회 루프
            while (rs.next()) {
                // 각 행 데이터 담을 HashMap 생성
                Map<String, Object> post = new HashMap<>();
                // rs.getXXX("컬럼명")으로 데이터 추출 후 post 맵에 .put()으로 저장
                post.put("post_id", rs.getInt("post_id"));
                post.put("title", rs.getString("title"));
                post.put("user_id", rs.getString("user_id"));
                post.put("author_name", rs.getString("author_name")); // 작성자 이름
                post.put("created_at", rs.getTimestamp("created_at"));
                // [수정됨] pinned_order 읽는 코드 제거
                // post.put("pinned_order", rs.getObject("pinned_order", Integer.class));
                // [유지] view_count 읽기 (정수형)
                post.put("view_count", rs.getInt("view_count"));
                // postList 리스트에 .add()로 post 맵 추가
                postList.add(post);
            }
        } catch (SQLException e) { // SQLException 예외 처리
            e.printStackTrace(); // 콘솔에 에러 로그 출력
        }
        // 조회된 게시글 목록 반환
        return postList;
    }

    /**
     * posts 테이블에 새로운 게시글 저장 메소드
     * @param post 저장할 게시글 정보 (Map) ('title', 'content', 'user_id' 포함)
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0)
     */
    public int save(Map<String, Object> post) {
        // SqlLoader.getSql() 호출하여 SQL 로드
        String sql = SqlLoader.getSql("post.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // SQL의 ? 파라미터 값 설정 (pstmt.setXXX() 메소드)
            // post 맵에서 .get()으로 값 추출 및 형변환
            pstmt.setString(1, (String) post.get("title"));
            pstmt.setString(2, (String) post.get("content"));
            pstmt.setString(3, (String) post.get("user_id"));

            // pstmt.executeUpdate(): INSERT/UPDATE/DELETE SQL 실행 후 영향받은 행 수 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0; // 실패 시 0 반환
        }
    }

    /**
     * post_id로 특정 게시글 하나 조회 메소드
     * @param postId 조회할 게시글 ID (int)
     * @return 게시글 정보 (Optional<Map<String, Object>>) (없으면 Optional.empty() 반환)
     */
    public Optional<Map<String, Object>> findById(int postId) {
        // SqlLoader.getSql() 호출하여 SQL 로드 ('pinned_order' 없는 SQL 사용)
        String sql = SqlLoader.getSql("post.select.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // SQL ? 파라미터 설정 (pstmt.setInt())
            pstmt.setInt(1, postId);

            // try-with-resources: ResultSet 자동 해제
            try (ResultSet rs = pstmt.executeQuery()) {
                // rs.next(): 결과 행이 존재하는지 확인 (존재하면 true)
                if (rs.next()) {
                    Map<String, Object> post = new HashMap<>();
                    // rs.getXXX()으로 데이터 추출 및 post 맵에 저장
                    post.put("post_id", rs.getInt("post_id"));
                    post.put("title", rs.getString("title"));
                    post.put("content", rs.getString("content"));
                    post.put("user_id", rs.getString("user_id"));
                    post.put("author_name", rs.getString("author_name"));
                    post.put("created_at", rs.getTimestamp("created_at"));
                    // [수정됨] pinned_order 읽는 코드 제거
                    // post.put("pinned_order", rs.getObject("pinned_order", Integer.class));
                    // [유지] view_count 읽기
                    post.put("view_count", rs.getInt("view_count"));
                    // Optional.of(): 조회된 post 맵을 Optional 객체로 감싸서 반환
                    return Optional.of(post);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // 데이터 없거나 오류 시 Optional.empty() 반환
        return Optional.empty();
    }

    /**
     * post_id로 특정 게시글 제목/내용 수정 메소드
     * @param post 수정할 게시글 정보 (Map) ('post_id', 'title', 'content' 포함)
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0)
     */
    public int update(Map<String, Object> post) {
        // SqlLoader.getSql() 호출하여 SQL 로드
        String sql = SqlLoader.getSql("post.update");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL ? 파라미터 설정
            pstmt.setString(1, (String) post.get("title"));
            pstmt.setString(2, (String) post.get("content"));
            pstmt.setInt(3, (Integer) post.get("post_id"));

            // pstmt.executeUpdate() 실행
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * post_id로 특정 게시글 삭제 메소드
     * @param postId 삭제할 게시글 ID (int)
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0)
     */
    public int delete(int postId) {
        // SqlLoader.getSql() 호출하여 SQL 로드
        String sql = SqlLoader.getSql("post.delete.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL ? 파라미터 설정
            pstmt.setInt(1, postId);
            // pstmt.executeUpdate() 실행
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * [유지] post_id로 특정 게시글 조회수 1 증가 메소드
     * @param postId 조회수 증가시킬 게시글 ID (int)
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0)
     */
    public int incrementViewCount(int postId) {
        // SqlLoader.getSql() 호출하여 SQL 로드
        String sql = SqlLoader.getSql("post.increment.view_count");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL ? 파라미터 설정
            pstmt.setInt(1, postId);
            // pstmt.executeUpdate() 실행
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}