package com.example.demo.dao;

import org.springframework.beans.factory.annotation.Autowired; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import org.springframework.stereotype.Repository; // 이 클래스가 DB와 통신하는 부품임을 알리는 도구

import javax.sql.DataSource; // 데이터베이스 연결 정보를 관리하는 도구
import java.sql.*; // Connection, PreparedStatement, ResultSet 등 JDBC(자바 DB 연결) 관련 핵심 도구들
import java.util.ArrayList; // 여러 데이터를 목록 형태로 다루기 위한 도구
import java.util.HashMap; // 데이터를 '이름표-값' 쌍으로 다루기 위한 도구
import java.util.List; // ArrayList의 상위 설계도
import java.util.Map; // HashMap의 상위 설계도
import java.util.Optional; // 'null'일 수도 있는 값을 안전하게 다루기 위한 포장지

@Repository
public class BoardDAO {

    @Autowired
    private DataSource dataSource;

    // 데이터베이스와 실제 연결을 생성하는 private 메소드
    private Connection getConnection() throws SQLException {
        // dataSource 객체로부터 현재 사용 가능한 DB 연결 통로(Connection)를 하나 빌려와서 반환
        return dataSource.getConnection();
    }

    /**
     * posts 테이블의 모든 게시글 목록을 조회합니다.
     * @return 게시글 목록 (List of Maps)
     */
    //조회 결과를 담을 비어있는 리스트
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> postList = new ArrayList<>();
        //sqlLoader로부터 'post.select.all' 키에 해당하는 SQL 명령어를 가져옴
        String sql = SqlLoader.getSql("post.select.all");

        //try-with-resources: 이 블록이 끝나면 conn, pstmt, rs 같은 자원들이 자동반환
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            //rs.next(): 결과(ResultSet)에서 다음 행이 있는지 확인
            while (rs.next()) {
                //각 행의 데이터를 담을 맵 생성
                Map<String, Object> post = new HashMap<>();
                //post_id는 INT(정수)이므로 getInt로 읽어와서  저장
                post.put("post_id", rs.getInt("post_id"));
                //title은 VARCHAR(문자열)이므로 getString으로 읽어와서 저장
                post.put("title", rs.getString("title"));
                //content도 VARCHAR(문자열)이므로 getString으로 읽어와서 저장
                post.put("user_id", rs.getString("user_id"));
                //SQL 쿼리의 별명(as)으로 지정한 'author_name' 값을 읽어와 저장
                post.put("author_name", rs.getString("author_name")); // 작성자 이름 추가
                //created_at은 TIMESTAMP(날짜+시간)이므로 getTimestamp로 읽어와서 저장
                post.put("created_at", rs.getTimestamp("created_at"));
                //완성된 게시글 데이터를 리스트에 추가
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
    public int save(Map<String, Object> post) { // post Map에는 'title', 'content', 'user_id' 키가 있어야 합니다.
        String sql = SqlLoader.getSql("post.insert"); // 'post.insert' 키에 해당하는 SQL 명령어를 가져옴
        try (Connection conn = getConnection(); // DB 연결 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // SQL의 물음표(?) 부분에 게시글 데이터 채우기    
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
    public Optional<Map<String, Object>> findById(int postId) { // postId: 조회할 게시글의 고유 ID
        String sql = SqlLoader.getSql("post.select.by_id"); // 'post.select.by_id' 키에 해당하는 SQL 명령어를 가져옴
        try (Connection conn = getConnection(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId); 
            // SQL의 ? 부분에 postId 값을 채웁니다.
            
            // SQL을 실행하고, 그 결과를 ResultSet(rs)
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) { // 조회된 게시글이 있으면
                    Map<String, Object> post = new HashMap<>();
                    //조회된 게시글 정보를 담을 새로운 Map 생성
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
    // 게시글 데이터에 'post_id', 'title', 'content' 키가 있어야 합니다.
    public int update(Map<String, Object> post) {
        String sql = SqlLoader.getSql("post.update");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL의 물음표(?) 부분에 수정할 게시글 데이터 채우기
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
    // postId: 삭제할 게시글의 고유 ID
    public int delete(int postId) {
        String sql = SqlLoader.getSql("post.delete.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL의 물음표(?) 부분에 postId 값 채우기
            pstmt.setInt(1, postId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}