package com.example.demo.dao; // 패키지 선언

// 필요한 클래스 import
import java.sql.Connection; // @Autowired 어노테이션 import
import java.sql.PreparedStatement; // @Repository 어노테이션 import
import java.sql.ResultSet; // DataSource 인터페이스 import (DB 커넥션 풀)
import java.sql.SQLException; // JDBC API 클래스들 import (Connection, PreparedStatement, ResultSet, SQLException)
import java.sql.Statement; // ArrayList 클래스 import (List 구현체)
import java.sql.Types; // HashMap 클래스 import (Map 구현체)
import java.util.ArrayList; // List 인터페이스 import
import java.util.HashMap; // Map 인터페이스 import
import java.util.List; // Optional 클래스 import (null 처리용)
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

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
     * [수정됨] posts 테이블의 특정 범위 게시글 목록 조회 메소드 (페이지네이션 + 고정글 정렬 + **동적 검색**) 정의 시작
     * @param limit 가져올 개수 (int) - 파라미터 설명
     * @param offset 건너뛸 개수 (int) - 파라미터 설명
     * @param searchType [] 검색 타입 (String, 예: "author", "content", "title") - 파라미터 설명
     * @param searchKeyword [] 검색어 (String) - 파라미터 설명
     * @return 게시글 목록 (List<Map<String, Object>>) - 반환 타입 설명
     */
    // [유지] searchType, searchKeyword 파라미터 2개 추가
    public List<Map<String, Object>> findAll(int limit, int offset, String searchType, String searchKeyword) {
        // 결과 담을 ArrayList 객체 생성 및 postList 변수 초기화
        List<Map<String, Object>> postList = new ArrayList<>();
        
        // [유지] 동적 SQL 조립을 위한 StringBuilder 사용
        // [유지] 기본 SELECT SQL문 (FROM posts p, LEFT JOIN users u)
        StringBuilder sql = new StringBuilder(
            "SELECT p.post_id, p.title, p.user_id, u.name as author_name, p.created_at, p.view_count, p.pinned_order " +
            "FROM posts p LEFT JOIN users u ON p.user_id = u.user_id "
        );

        // [유지] PreparedStatement의 ? 에 바인딩할 파라미터를 순서대로 담을 리스트
        List<Object> params = new ArrayList<>();

        // [유지] WHERE 절을 동적으로 조립 (검색어가 없어도 SQL 오류가 나지 않도록 1=1 기본값 추가)
        StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");
        
        // [유지] 검색어(searchKeyword)가 null이 아니고 빈 문자열("")이 아닐 경우에만
        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            // ▼▼▼ [ 추가] '제목' 검색 로직 ▼▼▼
            // [] 만약 검색 타입(searchType)이 "title"(제목)이라면
            if ("title".equals(searchType)) {
                // [] WHERE 절에 '게시글 제목(p.title) LIKE ?' 조건 추가
                whereSql.append(" AND p.title LIKE ? ");
                // [] 파라미터 리스트에 ? 에 바인딩할 값(검색어) 추가
                params.add("%" + searchKeyword + "%");
            }
            // ▲▲▲ [ 추가] '제목' 검색 로직 끝 ▲▲▲
            // [유지] 만약 검색 타입(searchType)이 "author"(작성자)라면
            else if ("author".equals(searchType)) {
                // [유지] WHERE 절에 '작성자 이름(u.name) LIKE ?' 조건 추가
                whereSql.append(" AND u.name LIKE ? ");
                // [유지] 파라미터 리스트에 ? 에 바인딩할 값(검색어) 추가 (SQL 인젝션 방지)
                params.add("%" + searchKeyword + "%");
            } 
            // [유지] 만약 검색 타입(searchType)이 "content"(내용)라면
            else if ("content".equals(searchType)) {
                // [유지] WHERE 절에 '게시글 내용(p.content) LIKE ?' 조건 추가
                whereSql.append(" AND p.content LIKE ? ");
                // [유지] 파라미터 리스트에 ? 에 바인딩할 값(검색어) 추가
                params.add("%" + searchKeyword + "%");
            }
            // ▼▼▼ [ 추가] '제목+내용' 검색 로직 ▼▼▼
            // [] 만약 검색 타입(searchType)이 "title_content"(제목+내용)라면
            else if ("title_content".equals(searchType)) {
                // [] WHERE 절에 '제목 LIKE ? 또는 내용 LIKE ?' (OR) 조건 추가
                whereSql.append(" AND (p.title LIKE ? OR p.content LIKE ?) ");
                // [] 파라미터 리스트에 첫 번째 ? (p.title) 값 추가
                params.add("%" + searchKeyword + "%");
                // [] 파라미터 리스트에 두 번째 ? (p.content) 값 추가
                params.add("%" + searchKeyword + "%");
            }
            // ▲▲▲ [ 추가] '제목+내용' 검색 로직 끝 ▲▲▲
            // [유지] 만약 검색 타입(searchType)이 "author_content"(작성자+내용)라면
            else if ("author_content".equals(searchType)) {
                // [유지] WHERE 절에 '작성자 이름 LIKE ? 또는 게시글 내용 LIKE ?' (OR) 조건 추가
                whereSql.append(" AND (u.name LIKE ? OR p.content LIKE ?) ");
                // [유지] 파라미터 리스트에 첫 번째 ? (u.name) 값 추가
                params.add("%" + searchKeyword + "%");
                // [유지] 파라미터 리스트에 두 번째 ? (p.content) 값 추가
                params.add("%" + searchKeyword + "%");
            }
            // [유지] 만약 검색 타입(searchType)이 "time"(작성된 시간)이라면
            else if ("time".equals(searchType)) {
                // [유지] WHERE 절에 'p.created_at의 시간(HOUR) = ?' 조건 추가
                whereSql.append(" AND HOUR(p.created_at) = ? ");
                // [유지] 파라미터 리스트에 ? 에 바인딩할 값(검색어, 예: '15') 추가
                params.add(searchKeyword);
            }
        }

        // [유지] 기본 SELECT SQL에 동적으로 생성된 WHERE 절 추가
        sql.append(whereSql);
        // [유지] 고정글/최순 정렬을 위한 ORDER BY 절 추가
        sql.append(" ORDER BY CASE WHEN p.pinned_order IS NULL THEN 1 ELSE 0 END ASC, p.pinned_order ASC, p.post_id DESC ");
        // [유지] 페이지네이션을 위한 LIMIT/OFFSET 절 추가
        sql.append(" LIMIT ? OFFSET ? ");

        // [유지] 파라미터 리스트에 LIMIT ? 에 바인딩할 값(limit) 추가
        params.add(limit);
        // [유지] 파라미터 리스트에 OFFSET ? 에 바인딩할 값(offset) 추가
        params.add(offset);


        // try-with-resources: conn, pstmt 자동 자원 해제 시작
        try (Connection conn = getConnection(); // getConnection() 호출하여 Connection 객체 생성
             // [유지] 완성된 동적 SQL 문자열(sql.toString())로 PreparedStatement 생성
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            // [유지] 파라미터 리스트(params)를 순회하며
            for (int i = 0; i < params.size(); i++) {
                // [유지] PreparedStatement의 ? 순서(i+1)에 맞게 값 바인딩 (SQL 인젝션 방지)
                pstmt.setObject(i + 1, params.get(i));
            }

            // try-with-resources: rs 자동 자원 해제 시작
            try (ResultSet rs = pstmt.executeQuery()) {
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
                    post.put("pinned_order", rs.getObject("pinned_order", Integer.class));
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
    } // findAll(int, int, String, String) 메소드 끝

    /**
     * [수정됨] posts 테이블의 전체 게시글 수 조회 메소드 (**검색 조건 포함**) 정의 시작
     * @param searchType [] 검색 타입 (String) - 파라미터 설명
     * @param searchKeyword [] 검색어 (String) - 파라미터 설명
     * @return 전체 게시글 수 (int) - 반환 타입 설명
     */
    // [유지] searchType, searchKeyword 파라미터 2개 추가
    public int countAll(String searchType, String searchKeyword) {
        
        // [유지] 동적 SQL 조립을 위한 StringBuilder 사용
        // [유지] 기본 SELECT COUNT(*) SQL문 (FROM posts p, LEFT JOIN users u)
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM posts p LEFT JOIN users u ON p.user_id = u.user_id "
        );
        
        // [유지] PreparedStatement의 ? 에 바인딩할 파라미터를 순서대로 담을 리스트
        List<Object> params = new ArrayList<>();
        
        // [유지] WHERE 절을 동적으로 조립 (findAll과 동일한 로직)
        StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");

        // [유지] 검색어(searchKeyword)가 null이 아니고 빈 문자열("")이 아닐 경우에만
        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            // ▼▼▼ [ 추가] '제목' 검색 로직 (findAll과 동일) ▼▼▼
            // [] 만약 검색 타입(searchType)이 "title"(제목)이라면
            if ("title".equals(searchType)) {
                // [] WHERE 절에 '게시글 제목(p.title) LIKE ?' 조건 추가
                whereSql.append(" AND p.title LIKE ? ");
                // [] 파라미터 리스트에 ? 에 바인딩할 값(검색어) 추가
                params.add("%" + searchKeyword + "%");
            }
            // ▲▲▲ [ 추가] '제목' 검색 로직 끝 ▲▲▲
            // [유지] 만약 검색 타입(searchType)이 "author"(작성자)라면
            else if ("author".equals(searchType)) {
                // [유지] WHERE 절에 '작성자 이름(u.name) LIKE ?' 조건 추가
                whereSql.append(" AND u.name LIKE ? ");
                // [유지] 파라미터 리스트에 ? 에 바인딩할 값(검색어) 추가
                params.add("%" + searchKeyword + "%");
            }
            // [유지] 만약 검색 타입(searchType)이 "content"(내용)라면
            else if ("content".equals(searchType)) {
                // [유지] WHERE 절에 '게시글 내용(p.content) LIKE ?' 조건 추가
                whereSql.append(" AND p.content LIKE ? ");
                // [유지] 파라미터 리스트에 ? 에 바인딩할 값(검색어) 추가
                params.add("%" + searchKeyword + "%");
            }
            // ▼▼▼ [ 추가] '제목+내용' 검색 로직 (findAll과 동일) ▼▼▼
            // [] 만약 검색 타입(searchType)이 "title_content"(제목+내용)라면
            else if ("title_content".equals(searchType)) {
                // [] WHERE 절에 '제목 LIKE ? 또는 내용 LIKE ?' (OR) 조건 추가
                whereSql.append(" AND (p.title LIKE ? OR p.content LIKE ?) ");
                // [] 파라미터 리스트에 첫 번째 ? (p.title) 값 추가
                params.add("%" + searchKeyword + "%");
                // [] 파라미터 리스트에 두 번째 ? (p.content) 값 추가
                params.add("%" + searchKeyword + "%");
            }
            // ▲▲▲ [ 추가] '제목+내용' 검색 로직 끝 ▲▲▲
            // [유지] 만약 검색 타입(searchType)이 "author_content"(작성자+내용)라면
            else if ("author_content".equals(searchType)) {
                // [유지] WHERE 절에 '작성자 이름 LIKE ? 또는 게시글 내용 LIKE ?' (OR) 조건 추가
                whereSql.append(" AND (u.name LIKE ? OR p.content LIKE ?) ");
                // [유지] 파라미터 리스트에 첫 번째 ? (u.name) 값 추가
                params.add("%" + searchKeyword + "%");
                // [유지] 파라미터 리스트에 두 번째 ? (p.content) 값 추가
                params.add("%" + searchKeyword + "%");
            }
            // [유지] 만약 검색 타입(searchType)이 "time"(작성된 시간)이라면
            else if ("time".equals(searchType)) {
                // [유지] WHERE 절에 'p.created_at의 시간(HOUR) = ?' 조건 추가
                whereSql.append(" AND HOUR(p.created_at) = ? ");
                // [유지] 파라미터 리스트에 ? 에 바인딩할 값(검색어, 예: '15') 추가
                params.add(searchKeyword);
            }
        }

        // [유지] 기본 SELECT COUNT SQL에 동적으로 생성된 WHERE 절 추가
        sql.append(whereSql);

        // try-with-resources: conn, pstmt, rs 자동 자원 해제 시작
        try (Connection conn = getConnection(); // Connection 객체 생성
             // [유지] 완성된 동적 SQL 문자열(sql.toString())로 PreparedStatement 생성
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            // [유지] 파라미터 리스트(params)를 순회하며
            for (int i = 0; i < params.size(); i++) {
                // [유지] PreparedStatement의 ? 순서(i+1)에 맞게 값 바인딩
                pstmt.setObject(i + 1, params.get(i));
            }

            // [유지] ResultSet을 try-with-resources 안으로 이동
            try (ResultSet rs = pstmt.executeQuery()) { // SQL 실행 및 ResultSet 객체 받음
                // rs.next(): 결과 행 존재 확인 (COUNT(*)는 1개 행 반환)
                if (rs.next()) {
                    // rs.getInt(1): 결과의 첫 번째 컬럼 값(전체 개수) 정수로 반환
                    return rs.getInt(1);
                } // if 끝
            } // [유지] rs 자동 해제
        } catch (SQLException e) { // SQLException 예외 처리 블록 시작
            e.printStackTrace(); // 콘솔 에러 로그 출력
        } // try-catch 끝 (conn, pstmt 자동 해제됨)
        return 0; // 오류 발생 시 0 반환
    } // countAll(String, String) 메소드 끝


    /**
     * posts 테이블에 새로운 게시글 저장 메소드 정의 시작
     * @param post 저장할 게시글 정보 (Map) ('title', 'content', 'user_id' 포함) - 파라미터 설명
     * @return 영향을 받은 행의 수 (성공 시 1, 실패 시 0) - 반환 타입 설명
     */
    public int save(Map<String, Object> post) { // save 메소드 정의 시작

    // SqlLoader.getSql() 호출하여 "post.insert" SQL 문자열 로드
    String sql = SqlLoader.getSql("post.insert");

    // try-with-resources: conn, pstmt 자동 자원 해제
    try (Connection conn = getConnection();

         // 자동 생성된 post_id 를 얻기 위해 RETURN_GENERATED_KEYS 사용
         PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        // SQL ? 파라미터 설정
        pstmt.setString(1, (String) post.get("title"));
        pstmt.setString(2, (String) post.get("content"));
        pstmt.setString(3, (String) post.get("user_id"));

        // INSERT 실행
        int rows = pstmt.executeUpdate();  // 영향받은 행 수

        // 자동 생성된 post_id 읽어오기
        try (ResultSet rs = pstmt.getGeneratedKeys()) {
            if (rs.next()) {
                int generatedId = rs.getInt(1);
                post.put("post_id", generatedId); // post Map에 post_id 저장
            }
        }

        return rows;

    } catch (SQLException e) {
        e.printStackTrace();
        return 0;
    }
}
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
                    //  pinned_order 읽기 (NULL 처리)
                    post.put("pinned_order", rs.getObject("pinned_order", Integer.class));
                    // [유지] view_count 읽기
                    post.put("view_count", rs.getInt("view_count")); // view_count 컬럼 (int)
                    // rs.getXXX() 메소드로 데이터 추출 및 post 맵에 .put() 메소드로 저장 끝
                    // Optional.of() 메소드: 조회된 post 맵을 Optional 객체로 감싸서 반환
                    return Optional.of(post);
                } // if 끝
            } // rs 자동 해제됨 (try-with-resOURCES 끝)
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
     *  post_id로 특정 게시글 고정 상태/순서 업데이트 메소드 정의 시작
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
     *  현재 고정된 게시글 수 조회 메소드 정의 시작 (3개 제한용)
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

    // ------------------------------------------------------
    // 에디터 이미지 사용 개수 카운트 (다른 글에서도 쓰는지 확인용)  
    // ------------------------------------------------------
    public int countPostsUsingEditorImage(String savedName) { 
        // posts.content 안에서 '/api/editor-images/view/파일명' 패턴 검색 
        String sql = SqlLoader.getSql("post.count.by_editor_image"); 

        String likePattern = "%/api/editor-images/view/" + savedName + "%"; 
        // try-with-resources: conn, pstmt, rs 자동 자원 해제 시작 
        try (Connection conn = getConnection(); // getconnection() DB연결을 하나 비려온다.
            PreparedStatement pstmt = conn.prepareStatement(sql)) { // 미리 준비된 SQL을 실행할 준비
            pstmt.setString(1, likePattern); // SQL 안 첫번째 ? 자리에 likePattern 값을 넣는다.

            try (ResultSet rs = pstmt.executeQuery()) { //DB에 전달해서 조회 실행
                if (rs.next()) { // 첫번째 행가져온다. 결과 있으면 true 없으면 false
                    return rs.getInt(1); //첫번째 칼럼 int로 가져온다.
                }
            } // rs 자동 해제됨 (try-with-resources 끝)
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
        return 0; 
    }

} // BoardDAO 클래스 정의 끝
