package com.example.demo.service.board; // 패키지 선언

import java.util.List; // List 인터페이스 import
import java.util.Map;  // Map 인터페이스 import

import org.springframework.web.multipart.MultipartFile; // 업로드 파일 처리용

/**
 * 게시판 비즈니스 로직을 정의하는 서비스 인터페이스
 */
public interface IBoardService { // IBoardService 인터페이스 정의 시작

    /**
     * 페이지네이션 + 검색이 적용된 게시글 목록 조회
     * @param page        요청 페이지 번호 (1부터 시작)
     * @param size        페이지당 게시글 수
     * @param searchType  검색 타입 (예: "title", "content", "writer" 등) - null 허용
     * @param searchKeyword 검색어 - null 허용
     * @return posts, totalPages, totalItems, currentPage 를 포함하는 Map
     */
    Map<String, Object> getAllPosts(int page, int size, String searchType, String searchKeyword);

    /**
     * 새 게시글 생성 + 첨부파일 저장
     * @param post    제목, 내용 등이 들어있는 Map (title, content 등)
     * @param files   업로드된 파일 목록 (없으면 null 또는 비어있는 리스트)
     * @param userId  작성자 ID
     * @return 생성된 게시글 정보(Map). 실패 시 null.
     */
    Map<String, Object> createPost(Map<String, Object> post, List<MultipartFile> files, String userId); // files 파라미터 추가

    /**
     * 특정 게시글 상세 조회
     * @param postId 게시글 ID
     * @return 게시글 정보(Map). 없으면 null.
     */
    Map<String, Object> getPost(int postId);

    /**
     * 게시글 수정 + 첨부파일 추가/삭제
     * @param postId        수정할 게시글 ID
     * @param postDetails   수정할 제목/내용 등이 들어있는 Map
     * @param newFiles      새로 추가할 파일 목록 (없으면 null 또는 비어있는 리스트)
     * @param deleteFileIds 삭제할 첨부파일 ID 목록 (없으면 null 또는 비어있는 리스트)
     * @param currentUserId 현재 로그인 사용자 ID
     * @return 수정된 게시글 정보(Map). 권한 없음/실패 시 null.
     */
    Map<String, Object> updatePost(
            int postId,
            Map<String, Object> postDetails,
            List<MultipartFile> newFiles,      //추가됨
            List<Integer> deleteFileIds,       //추가됨
            String currentUserId
    ); // 파일 관련 파라미터 추가

    /**
     * 게시글 삭제 (작성자 또는 ADMIN)
     * @param postId        삭제할 게시글 ID
     * @param currentUserId 현재 로그인 사용자 ID
     * @param roles         현재 사용자 역할 목록 (예: ["ADMIN", "USER"])
     * @return 삭제 성공 여부
     */
    boolean deletePost(int postId, String currentUserId, List<String> roles);

    /**
     * 게시글 조회수 증가
     * @param postId 게시글 ID
     */
    void incrementViewCount(int postId);

    /**
     * 게시글 고정 (관리자 전용, 최대 3개 등 제약은 구현체에서 처리)
     * @param postId        고정할 게시글 ID
     * @param order         고정 순서 (예: 1, 2, 3)
     * @param currentUserId 현재 로그인 사용자 ID
     * @param roles         현재 사용자 역할 목록
     * @return 고정 성공 여부
     */
    boolean pinPost(int postId, int order, String currentUserId, List<String> roles);

    /**
     * 게시글 고정 해제 (관리자 전용)
     * @param postId        고정 해제할 게시글 ID
     * @param currentUserId 현재 로그인 사용자 ID
     * @param roles         현재 사용자 역할 목록
     * @return 고정 해제 성공 여부
     */
    boolean unpinPost(int postId, String currentUserId, List<String> roles);

} // IBoardService 인터페이스 정의 끝
