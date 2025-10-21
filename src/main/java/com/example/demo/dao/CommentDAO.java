package com.example.demo.dao;

import org.springframework.beans.factory.annotation.Autowired; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import org.springframework.stereotype.Repository; // 이 클래스가 DB와 통신하는 부품임을 알리는 도구
import javax.sql.DataSource; // 데이터베이스 연결 정보를 관리하는 도구
import java.sql.*; // Connection, PreparedStatement, ResultSet 등 JDBC(자바 DB 연결) 관련 핵심 도구들
import java.util.*; // ArrayList, HashMap, List, Map, Optional 등 자바의 여러 유용한 도구들

@Repository
public class CommentDAO {

    @Autowired
    private DataSource dataSource;

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // 특정 게시글의 모든 댓글 조회

    public List<Map<String, Object>> findByPostId(int postId) {
        //조회 결과를 담을 비어있는 리스트(commentList)를 생성
        List<Map<String, Object>> commentList = new ArrayList<>();
        // SqlLoader를 통해 'comment.select.by_post_id' 키에 해당하는 SQL 명령어
        String sql = SqlLoader.getSql("comment.select.by_post_id");
        //try-with-resources: 이 블록이 끝나면 conn, pstmt, rs 같은 자원들이 자동반환
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
                //// try-with-resources: 이 블록이 끝나면 conn과 pstmt가 자동
            pstmt.setInt(1, postId);
            try (ResultSet rs = pstmt.executeQuery()) { // ResultSet rs도 자동 반환
                while (rs.next()) { //rs.next(): 결과(ResultSet)에서 다음 행이 있는지 확인
                    Map<String, Object> comment = new HashMap<>();
                    //// 현재 행의 각 컬럼 값을 읽어와서, Map에 이름표(key)와 함께 저장
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
    // Optional<Map<String, Object>>: 댓글이 없을 수도 있으므로 Optional로 감쌈
    public Optional<Map<String, Object>> findById(int commentId) {
        // SqlLoader로부터 'comment.select.by_id' 키에 해당하는 SQL 명령어를 가져옴
        String sql = SqlLoader.getSql("comment.select.by_id");
        //try-with-resources: 이 블록이 끝나면 conn과 pstmt가 자동 반환
        try (Connection conn = getConnection();
            // PreparedStatement pstmt: SQL 명령어를 실행하기 위한 준비 도구
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, commentId);
            try (ResultSet rs = pstmt.executeQuery()) { // ResultSet rs도 자동 반환
                if (rs.next()) { //rs.next(): 결과(ResultSet)에서 다음 행이 있는지 확인
                    Map<String, Object> comment = new HashMap<>(); //각 행의 데이터를 담을 맵 생성
                    comment.put("comment_id", rs.getInt("comment_id"));
                    comment.put("post_id", rs.getInt("post_id"));
                    comment.put("content", rs.getString("content"));
                    comment.put("user_id", rs.getString("user_id"));
                    comment.put("created_at", rs.getTimestamp("created_at"));
                    return Optional.of(comment);//조회된 댓글 데이터를 Optional로 감싸서 반환
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    // 댓글 생성
    // comment Map에는 'post_id', 'content', 'user_id' 키가 있어야 합니다.
    public int save(Map<String, Object> comment) { 
        String sql = SqlLoader.getSql("comment.insert"); // 'comment.insert' 키에 해당하는 SQL 명령어를 가져옴
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
    // comment Map에는 'comment_id', 'content' 키가 있어야 합니다.
    public int update(Map<String, Object> comment) { 
        String sql = SqlLoader.getSql("comment.update"); // 'comment.update' 키에 해당하는 SQL 명령어를 가져옴
        //try-with-resources: 이 블록이 끝나면 conn과 pstmt가 자동 반환
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
    // commentId: 삭제할 댓글의 고유 ID
    public int delete(int commentId) {
        // 'comment.delete.by_id' 키에 해당하는 SQL 명령어를 가져옴
        String sql = SqlLoader.getSql("comment.delete.by_id");
        //try-with-resources: 이 블록이 끝나면 conn과 pstmt가 자동 반환
        try (Connection conn = getConnection();
            // PreparedStatement pstmt: SQL 명령어를 실행하기 위한 준비 도구
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, commentId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}