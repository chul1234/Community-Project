package com.example.demo.dao; // 패키지 선언

// 필요한 클래스 import
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 어노테이션 import
import org.springframework.stereotype.Repository; // @Repository 어노테이션 import

import javax.sql.DataSource; // DataSource 인터페이스 import (DB 커넥션 풀)
import java.sql.*; // JDBC API 클래스들 import (Connection, PreparedStatement, ResultSet, SQLException)
import java.util.ArrayList; // ArrayList 클래스 import (List 구현체)
import java.util.HashMap; // HashMap 클래스 import (Map 구현체)
import java.util.List; // List 인터페이스 import
import java.util.Map; // Map 인터페이스 import
import java.util.Optional; // Optional 클래스 import (null 처리용)

@Repository // @Repository: 데이터 접근 계층(DAO) 컴포넌트 선언
public class BoardDAO { // BoardDAO 클래스 정의 시작

    @Autowired // @Autowired: Spring 컨테이너가 DataSource 타입 Bean 자동 주입
    private DataSource dataSource; // DataSource 객체 멤버 변수 선언

    // getConnection(): 데이터 소스에서 DB Connection 객체 획득 private 메소드 정의
    private Connection getConnection() throws SQLException { // SQLException 처리 위임
        // dataSource.getConnection() 메소드 호출하여 Connection 반환
        return dataSource.getConnection();
    } // getConnection() 메소드 끝

    /**
     * [수정됨] posts 테이블의 특정 범위 게시글 목록 조회 메소드 (페이지네이션 + 고정글 정렬) 정의 시작
     * @param limit 가져올 개수 (int) - 파라미터 설명
     * @param offset 건너뛸 개수 (int) - 파라미터 설명
     * @return 게시글 목록 (List<Map<String, Object>>) - 반환 타입 설명
     */
    public List<Map<String, Object>> findAll(int limit, int offset) { // limit, offset 파라미터 받음
        // 결과 담을 ArrayList 객체 생성 및 postList 변수 초기화
        List<Map<String, Object>> postList = new ArrayList<>();
        // SqlLoader.getSql() 메소드 호출하여 "post.select.all" SQL 문자열 로드 (고정글 정렬 SQL)
        String sql = SqlLoader.getSql("post.select.all");

        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // getConnection() 호출하여 Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // Connection 객체로 PreparedStatement 생성

            // [유지] SQL의 ? 파라미터 값 설정 시작 (LIMIT, OFFSET)
            pstmt.setInt(1, limit);  // 첫 번째 ? 에 limit 값 설정 (setInt 메소드 사용)
            pstmt.setInt(2, offset); // 두 번째 ? 에 offset 값 설정 (setInt 메소드 사용)
            // [유지] SQL의 ? 파라미터 값 설정 끝

            // try-with-resources: rs 자동 자원 해제 시작
            try (ResultSet rs = pstmt.executeQuery()) { // pstmt.executeQuery() 메소드 호출하여 SQL 실행 및 ResultSet 객체 받음
                // rs.next(): ResultSet 커서 다음 행 이동 루프 시작 (데이터 있으면 true)
                while (rs.next()) {
                    // 각 행 데이터 담을 HashMap 객체 생성 및 post 변수 초기화
                    Map<String, Object> post = new HashMap<>();
                    // rs.getXXX("컬럼명") 메소드 호출하여 데이터 추출 후 post 맵에 .put() 메소드로 저장 시작
                    post.put("post_id", rs.getInt("post_id")); // post_id 컬럼 (int)
                    post.put("title", rs.getString("title")); // title 컬럼 (String)
                    post.put("user_id", rs.getString("user_id")); // user_id 컬럼 (String)
                    post.put("author_name", rs.getString("author_name")); // author_name 별명 컬럼 (String)
                    post.put("created_at", rs.getTimestamp("created_at")); // created_at 컬럼 (Timestamp)
                    // [신규 추가됨] pinned_order 읽기 (정수형). rs.getObject() 사용하여 NULL 처리
                    post.put("pinned_order", rs.getObject("pinned_order", Integer.class));
                    // [유지] view_count 읽기 (정수형)
                    post.put("view_count", rs.getInt("view_count")); // view_count 컬럼 (int)
                    // rs.getXXX("컬럼명") 메소드 호출하여 데이터 추출 후 post 맵에 .put() 메소드로 저장 끝
                    // postList 리스트에 .add() 메소드로 post 맵 추가
                    postList.add(post);
                } // rs.next() 루프 끝
            } // rs 자동 해제됨 (try-with-resources 끝)
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 예외 발생 시 콘솔 에러 로그 출력 (printStackTrace 메소드 사용)
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
        // 조회된 게시글 목록(postList) 반환
        return postList;
    } // findAll(int, int) 메소드 끝

    /**
     * [유지] posts 테이블의 전체 게시글 수 조회 메소드 정의 시작
     * @return 전체 게시글 수 (int) - 반환 타입 설명
     */
    public int countAll() { // countAll 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.count.all" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.count.all");
        // try-with-resources: conn, pstmt, rs 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql); // PreparedStatement 객체 생성
             ResultSet rs = pstmt.executeQuery()) { // SQL 실행 및 ResultSet 객체 받음
            // rs.next(): 결과 행 존재 확인 (COUNT(*)는 1개 행 반환)
            if (rs.next()) {
                // rs.getInt(1): 결과의 첫 번째 컬럼 값(전체 개수) 정수로 반환
                return rs.getInt(1);
            } // if 끝
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
        } // try-catch 끝 (conn, pstmt, rs 자동 해제됨)
        return 0; // 오류 발생 시 0 반환
    } // countAll 메소드 끝


    /**
     * posts 테이블에 새로운 게시글 저장 메소드 정의 시작
     * @param post 저장할 게시글 정보 (Map) ('title', 'content', 'user_id' 포함) - 파라미터 설명
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0) - 반환 타입 설명
     */
    public int save(Map<String, Object> post) { // save 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.insert" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.insert");
        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 객체 생성

            // SQL의 ? 파라미터 값 설정 시작 (pstmt.setXXX() 메소드 사용)
            // post 맵에서 .get() 메소드로 값 추출 및 형변환
            pstmt.setString(1, (String) post.get("title")); // 첫 번째 ? : title (String)
            pstmt.setString(2, (String) post.get("content")); // 두 번째 ? : content (String)
            pstmt.setString(3, (String) post.get("user_id")); // 세 번째 ? : user_id (String)
            // SQL의 ? 파라미터 값 설정 끝

            // pstmt.executeUpdate(): INSERT SQL 실행 후 영향받은 행 수 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
            return 0; // 실패 시 0 반환
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
    } // save 메소드 끝

    /**
     * post_id로 특정 게시글 하나 조회 메소드 정의 시작
     * @param postId 조회할 게시글 ID (int) - 파라미터 설명
     * @return 게시글 정보 (Optional<Map<String, Object>>) (없으면 Optional.empty() 반환) - 반환 타입 설명
     */
    public Optional<Map<String, Object>> findById(int postId) { // findById 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.select.by_id" SQL 문자열 로드 (pinned_order 포함 SQL)
        String sql = SqlLoader.getSql("post.select.by_id");
        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 객체 생성

            // SQL ? 파라미터 설정 (pstmt.setInt() 메소드 사용)
            pstmt.setInt(1, postId); // 첫 번째 ? : postId (int)

            // try-with-resources: ResultSet 자동 해제 시작
            try (ResultSet rs = pstmt.executeQuery()) { // SQL 실행 및 ResultSet 객체 받음
                // rs.next(): 결과 행 존재 확인 (존재하면 true)
                if (rs.next()) {
                    // 결과 담을 HashMap 객체 생성
                    Map<String, Object> post = new HashMap<>();
                    // rs.getXXX() 메소드로 데이터 추출 및 post 맵에 .put() 메소드로 저장 시작
                    post.put("post_id", rs.getInt("post_id")); // post_id 컬럼 (int)
                    post.put("title", rs.getString("title")); // title 컬럼 (String)
                    post.put("content", rs.getString("content")); // content 컬럼 (String)
                    post.put("user_id", rs.getString("user_id")); // user_id 컬럼 (String)
                    post.put("author_name", rs.getString("author_name")); // author_name 별명 컬럼 (String)
                    post.put("created_at", rs.getTimestamp("created_at")); // created_at 컬럼 (Timestamp)
                    // [신규 추가됨] pinned_order 읽기 (NULL 처리)
                    post.put("pinned_order", rs.getObject("pinned_order", Integer.class));
                    // [유지] view_count 읽기
                    post.put("view_count", rs.getInt("view_count")); // view_count 컬럼 (int)
                    // rs.getXXX() 메소드로 데이터 추출 및 post 맵에 .put() 메소드로 저장 끝
                    // Optional.of() 메소드: 조회된 post 맵을 Optional 객체로 감싸서 반환
                    return Optional.of(post);
                } // if 끝
            } // rs 자동 해제됨 (try-with-resources 끝)
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
        // 데이터 없거나 오류 시 Optional.empty() 반환
        return Optional.empty();
    } // findById 메소드 끝

    /**
     * post_id로 특정 게시글 제목/내용 수정 메소드 정의 시작
     * @param post 수정할 게시글 정보 (Map) ('post_id', 'title', 'content' 포함) - 파라미터 설명
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0) - 반환 타입 설명
     */
    public int update(Map<String, Object> post) { // update 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.update" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.update");
        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 객체 생성
            // SQL ? 파라미터 설정 시작
            pstmt.setString(1, (String) post.get("title")); // 첫 번째 ? : title (String)
            pstmt.setString(2, (String) post.get("content")); // 두 번째 ? : content (String)
            pstmt.setInt(3, (Integer) post.get("post_id")); // 세 번째 ? : post_id (int)
            // SQL ? 파라미터 설정 끝

            // pstmt.executeUpdate() 메소드: UPDATE SQL 실행 후 영향받은 행 수 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
            return 0; // 실패 시 0 반환
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
    } // update 메소드 끝

    /**
     * post_id로 특정 게시글 삭제 메소드 정의 시작
     * @param postId 삭제할 게시글 ID (int) - 파라미터 설명
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0) - 반환 타입 설명
     */
    public int delete(int postId) { // delete 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.delete.by_id" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.delete.by_id");
        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 객체 생성
            // SQL ? 파라미터 설정
            pstmt.setInt(1, postId); // 첫 번째 ? : postId (int)
            // pstmt.executeUpdate() 메소드: DELETE SQL 실행 후 영향받은 행 수 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
            return 0; // 실패 시 0 반환
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
    } // delete 메소드 끝

    /**
     * [유지] post_id로 특정 게시글 조회수 1 증가 메소드 정의 시작
     * @param postId 조회수 증가시킬 게시글 ID (int) - 파라미터 설명
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0) - 반환 타입 설명
     */
    public int incrementViewCount(int postId) { // incrementViewCount 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.increment.view_count" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.increment.view_count");
        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 객체 생성
            // SQL ? 파라미터 설정
            pstmt.setInt(1, postId); // 첫 번째 ? : postId (int)
            // pstmt.executeUpdate() 메소드: UPDATE SQL 실행 후 영향받은 행 수 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
            return 0; // 실패 시 0 반환
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
    } // incrementViewCount 메소드 끝

    /**
     * [신규 추가됨] post_id로 특정 게시글 고정 상태/순서 업데이트 메소드 정의 시작
     * @param postId 업데이트할 게시글 ID (int) - 파라미터 설명
     * @param order 설정할 고정 순서 (Integer, null 가능 - null은 고정 해제 의미) - 파라미터 설명
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0) - 반환 타입 설명
     */
    public int updatePinnedOrder(int postId, Integer order) { // updatePinnedOrder 메소드 정의 시작, order 타입 Integer
        // SqlLoader.getSql() 호출하여 "post.update.pinned_order" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.update.pinned_order");
        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 객체 생성
            // SQL ? 파라미터 설정 시작
            if (order == null) { // 만약 order 값이 null이면 (고정 해제 시)
                // pstmt.setNull() 메소드: 첫 번째 ? 파라미터에 SQL NULL 값 설정 (Types.INTEGER는 JDBC 타입 코드)
                pstmt.setNull(1, Types.INTEGER);
            } else { // order 값이 null이 아니면 (고정 시)
                // pstmt.setInt() 메소드: 첫 번째 ? 파라미터에 order 값(int) 설정
                pstmt.setInt(1, order);
            } // if-else 끝
            pstmt.setInt(2, postId); // 두 번째 ? 파라미터에 postId (int) 설정
            // SQL ? 파라미터 설정 끝
            // pstmt.executeUpdate() 메소드: UPDATE SQL 실행 후 영향받은 행 수 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
            return 0; // 실패 시 0 반환
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
    } // updatePinnedOrder 메소드 끝

    /**
     * [신규 추가됨] 현재 고정된 게시글 수 조회 메소드 정의 시작 (3개 제한용)
     * @return 고정된 게시글 수 (int) - 반환 타입 설명
     */
    public int countPinned() { // countPinned 메소드 정의 시작
        // SqlLoader.getSql() 호출하여 "post.count.pinned" SQL 문자열 로드
        String sql = SqlLoader.getSql("post.count.pinned");
        // try-with-resources: conn, pstmt, rs 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             PreparedStatement pstmt = conn.prepareStatement(sql); // PreparedStatement 객체 생성
             ResultSet rs = pstmt.executeQuery()) { // SQL 실행 및 ResultSet 객체 받음
            // rs.next(): 결과 행 존재 확인 (COUNT(*)는 1개 행 반환)
            if (rs.next()) {
                // rs.getInt(1): 결과의 첫 번째 컬럼 값(고정된 개수) 정수로 반환
                return rs.getInt(1);
            } // if 끝
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
        } // try-catch 끝 (conn, pstmt, rs 자동 해제됨)
        return 0; // 오류 발생 시 0 반환
    } // countPinned 메소드 끝

} // BoardDAO 클래스 정의 끝