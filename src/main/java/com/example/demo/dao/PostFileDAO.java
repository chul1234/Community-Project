package com.example.demo.dao; // 파일이 속한 패키지 (DAO 계층)

// JDBC 관련 import
import java.sql.Connection;       // DB 연결 객체
import java.sql.PreparedStatement; // 미리 컴파일된 SQL 실행을 위한 객체
import java.sql.ResultSet;       // SELECT 결과를 담는 객체
import java.sql.SQLException;    // SQL 실행 중 발생하는 예외
import java.util.ArrayList;      // List 구현체 (ArrayList) 사용
import java.util.HashMap;        // Map 구현체 (HashMap) 사용
import java.util.List;           // List 인터페이스
import java.util.Map;            // Map 인터페이스
import java.util.Optional;       // 값이 있을 수도/없을 수도 있는 래퍼 타입

import javax.sql.DataSource;     // 커넥션 풀을 제공하는 DataSource

import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입
import org.springframework.stereotype.Repository;               // DAO 컴포넌트 표시

@Repository // 스프링에게 "이 클래스는 DB 접근을 담당하는 DAO"라고 알려주는 어노테이션
public class PostFileDAO {

    @Autowired // 스프링이 설정해둔 DataSource(커넥션 풀)를 자동으로 주입
    private DataSource dataSource;

    // 커넥션 풀(DataSource)에서 Connection 하나를 가져오는 헬퍼 메서드
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection(); // 커넥션 풀에서 DB 연결 객체 빌려오기
    }

    /**
     * 파일 메타데이터 INSERT
     * - post_files 테이블에 한 건의 파일 정보를 저장한다.
     * @param postId   게시글 ID
     * @param fileInfo original_name, saved_name, content_type, file_size, file_path 등이 담긴 Map
     * @return insert가 성공한 행 수 (보통 1, 실패 시 0)
     */
    public int insertFile(int postId, Map<String, Object> fileInfo) {
        String sql = SqlLoader.getSql("file.insert"); // sql.properties(or 외부 파일)에서 file.insert 쿼리 읽기

        // try-with-resources: try 블록이 끝나면 conn, pstmt 자동 close
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // ? 순서대로 파라미터 바인딩
            pstmt.setInt(1, postId);                                      // 1번째 ? → post_id
            pstmt.setString(2, (String) fileInfo.get("original_name"));   // 2번째 ? → 원본 파일명
            pstmt.setString(3, (String) fileInfo.get("saved_name"));      // 3번째 ? → 서버에 저장된 파일명
            pstmt.setString(4, (String) fileInfo.get("content_type"));    // 4번째 ? → MIME 타입
            pstmt.setLong(5, (Long) fileInfo.get("file_size"));           // 5번째 ? → 파일 크기
            pstmt.setString(6, (String) fileInfo.get("file_path"));       // 6번째 ? → 폴더 경로(폴더 업로드용)

            return pstmt.executeUpdate(); // INSERT 실행 후 영향받은 행 수 반환 (성공 시 1)
        } catch (SQLException e) {        // SQL 에러 발생 시
            e.printStackTrace();          // 스택 트레이스 출력(로그)
            return 0;                     // 실패로 보고 0 반환
        }
    }

    /**
     * 특정 게시글(postId)에 연결된 파일 목록 조회
     * @param postId 게시글 ID
     * @return 해당 글에 달린 파일들의 리스트 (각 파일은 Map 형태)
     */
    public List<Map<String, Object>> findByPostId(int postId) {
        String sql = SqlLoader.getSql("file.select.by_post_id"); // post_id 기준 파일 목록 조회용 SQL
        List<Map<String, Object>> list = new ArrayList<>();      // 결과를 담을 리스트

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId); // SQL의 첫 번째 ? 에 postId 바인딩

            try (ResultSet rs = pstmt.executeQuery()) { // SELECT 실행
                while (rs.next()) { // 결과 행을 한 줄씩 처리
                    Map<String, Object> file = new HashMap<>(); // 한 파일 정보를 담을 Map 생성
                    file.put("file_id", rs.getInt("file_id"));                 // PK
                    file.put("post_id", rs.getInt("post_id"));                 // 게시글 ID
                    file.put("original_name", rs.getString("original_name"));  // 원본 파일명
                    file.put("saved_name", rs.getString("saved_name"));        // 서버 저장 파일명
                    file.put("content_type", rs.getString("content_type"));    // MIME 타입
                    file.put("file_size", rs.getLong("file_size"));            // 파일 크기
                    file.put("file_path", rs.getString("file_path"));          // 폴더 경로(없으면 null/빈문자열)
                    list.add(file); // 리스트에 한 건 추가
                }
            }

        } catch (SQLException e) { // 쿼리 실행 중 예외 발생
            e.printStackTrace();   // 에러 로그 출력
        }

        return list; // 조회 결과(파일 목록) 반환 (없으면 빈 리스트)
    }

    /**
     * 특정 파일 한 개 조회
     * @param fileId 파일 PK
     * @return 파일 정보(Map)를 담은 Optional. 없으면 Optional.empty()
     */
    public Optional<Map<String, Object>> findById(int fileId) {
        String sql = SqlLoader.getSql("file.select.by_id"); // file_id 기준 한 건 조회용 SQL

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, fileId); // 첫 번째 ? 에 fileId 바인딩

            try (ResultSet rs = pstmt.executeQuery()) { // SELECT 실행
                if (rs.next()) { // 결과가 한 건이라도 있으면
                    Map<String, Object> file = new HashMap<>();  // 결과를 담을 Map 생성
                    file.put("file_id", rs.getInt("file_id"));                 // 파일 PK
                    file.put("post_id", rs.getInt("post_id"));                 // 게시글 ID
                    file.put("original_name", rs.getString("original_name"));  // 원본 파일명
                    file.put("saved_name", rs.getString("saved_name"));        // 저장 파일명
                    file.put("content_type", rs.getString("content_type"));    // MIME 타입
                    file.put("file_size", rs.getLong("file_size"));            // 파일 크기
                    file.put("file_path", rs.getString("file_path"));          // 파일 경로
                    return Optional.of(file); // 값이 있는 Optional로 감싸서 반환
                }
            }

        } catch (SQLException e) { // SQL 실행 중 예외
            e.printStackTrace();   // 에러 출력
        }

        // 결과가 없거나 예외가 발생하면 빈 Optional 반환
        return Optional.empty();
    }

    /**
     * 특정 파일 한 개 삭제
     * @param fileId 삭제할 파일 PK
     * @return 삭제된 행 수 (성공 시 1, 없으면 0)
     */
    public int deleteById(int fileId) {
        String sql = SqlLoader.getSql("file.delete.by_id"); // file_id로 삭제하는 SQL

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, fileId);        // 첫 번째 ? 에 fileId 바인딩
            return pstmt.executeUpdate();   // DELETE 실행 후 영향받은 행 수 반환

        } catch (SQLException e) {          // 예외 발생 시
            e.printStackTrace();            // 에러 로그
            return 0;                       // 실패로 보고 0 반환
        }
    }

    /**
     * 특정 게시글에 연결된 모든 파일 삭제
     * @param postId 게시글 ID
     * @return 삭제된 행 수 (삭제된 파일 개수)
     */
    public int deleteByPostId(int postId) {
        String sql = SqlLoader.getSql("file.delete.by_post_id"); // post_id 기준 전체 삭제 SQL

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, postId);        // 첫 번째 ? 에 postId 바인딩
            return pstmt.executeUpdate();   // 해당 게시글의 모든 파일 레코드 삭제

        } catch (SQLException e) {          // 예외 발생 시
            e.printStackTrace();            // 에러 출력
            return 0;                       // 실패로 0 반환
        }
    }
    
    
}
