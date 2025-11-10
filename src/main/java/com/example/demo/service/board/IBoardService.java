package com.example.demo.service.board; // 패키지 선언

import java.util.List; // List 인터페이스 import
import java.util.Map; // Map 인터페이스 import

// IBoardService 인터페이스 정의 시작
public interface IBoardService {

    /**
     * [수정됨] 특정 페이지의 게시글 목록과 전체 페이지 정보를 조회
     * @param page 요청 페이지 번호 (1부터 시작) - 파라미터 설명
     * @param size 페이지당 게시글 수 - 파라미터 설명
     * @param searchType [신규] 검색 타입 (String, 예: "author") - 파라미터 설명
     * @param searchKeyword [신규] 검색어 (String) - 파라미터 설명
     * @return Map (키: "posts", "totalPages", "totalItems", "currentPage") - 반환 타입 설명
     */
    // [신규] searchType, searchKeyword 파라미터 2개 추가
    Map<String, Object> getAllPosts(int page, int size, String searchType, String searchKeyword); // getAllPosts 메소드 선언

    /**
     * 게시글 생성
     * @param post 생성할 게시글 정보 (Map) - 파라미터 설명
     * @param userId 작성자 ID (String) - 파라미터 설명
     * @return 생성된 게시글 정보 (Map) 또는 null - 반환 타입 설명
     */
    Map<String, Object> createPost(Map<String, Object> post, String userId); // createPost 메소드 선언

    /**
     * 특정 게시글 조회
     * @param postId 조회할 게시글 ID (int) - 파라미터 설명
     * @return 게시글 정보 (Map) 또는 null - 반환 타입 설명
     */
    Map<String, Object> getPost(int postId); // getPost 메소드 선언

    /**
     * [유지] 게시글 수정
     * @param postId 수정할 게시글 ID (int) - 파라미터 설명
     * @param postDetails 수정할 내용 (Map) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 파라미터 설명
     * @return 수정된 게시글 정보 (Map) 또는 null (권한 없음 등) - 반환 타입 설명
     */
    Map<String, Object> updatePost(int postId, Map<String, Object> postDetails, String currentUserId); // updatePost 메소드 선언

    /**
     * [유지] 게시글 삭제 (관리자 또는 작성자)
     * @param postId 삭제할 게시글 ID (int) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 파라미터 설명
     * @param roles 현재 사용자 역할 목록 (List<String>) - 파라미터 설명
     * @return 삭제 성공 여부 (boolean) - 반환 타입 설명
     */
    boolean deletePost(int postId, String currentUserId, List<String> roles); // deletePost 메소드 선언

    /**
     * [유지] 특정 게시글의 조회수 증가
     * @param postId 조회수 증가시킬 게시글 ID (int) - 파라미터 설명
     */
    void incrementViewCount(int postId); // incrementViewCount 메소드 선언

    /**
     * [신규 추가] 게시글 고정 (관리자 전용)
     * @param postId 고정할 게시글 ID (int) - 파라미터 설명
     * @param order 고정 순서 (int, 예: 1, 2, 3) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 권한 확인용 파라미터 설명
     * @param roles 현재 사용자 역할 목록 (List<String>) - 권한 확인용 파라미터 설명
     * @return 고정 성공 여부 (boolean, 3개 제한 초과 시 false) - 반환 타입 설명
     */
    boolean pinPost(int postId, int order, String currentUserId, List<String> roles); // pinPost 메소드 선언

    /**
     * [신규 추가] 게시글 고정 해제 (관리자 전용)
     * @param postId 고정 해제할 게시글 ID (int) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 권한 확인용 파라미터 설명
     * @param roles 현재 사용자 역할 목록 (List<String>) - 권한 확인용 파라미터 설명
     * @return 고정 해제 성공 여부 (boolean) - 반환 타입 설명
     */
    boolean unpinPost(int postId, String currentUserId, List<String> roles); // unpinPost 메소드 선언

} // IBoardService 인터페이스 정의 끝