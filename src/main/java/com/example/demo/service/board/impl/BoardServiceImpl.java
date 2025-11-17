package com.example.demo.service.board.impl; // 패키지 선언


// 필요한 클래스 import
import java.util.HashMap; // BoardDAO 클래스 import
import java.util.List; // IBoardService 인터페이스 import
import java.util.Map; // @Autowired 어노테이션 import

import org.springframework.beans.factory.annotation.Autowired; // @Service 어노테이션 import
import org.springframework.stereotype.Service; // HashMap 클래스 import
import org.springframework.web.multipart.MultipartFile; // Map 인터페이스 import

import com.example.demo.dao.BoardDAO; // List 인터페이스 import
import com.example.demo.service.board.IBoardService;
import com.example.demo.service.file.IFileService; // ★ 추가됨: 파일 업로드 처리용

@Service // @Service: 서비스 계층 컴포넌트 선언
public class BoardServiceImpl implements IBoardService { // BoardServiceImpl 클래스 정의 시작, IBoardService 구현

    @Autowired // @Autowired: Spring이 BoardDAO Bean 자동 주입
    private BoardDAO boardDAO; // BoardDAO 객체 멤버 변수 선언

    @Autowired
    private IFileService fileService; // ★ 신규 추가: 첨부파일 관련 작업 담당

    /**
     * [수정됨] 특정 페이지의 게시글 목록과 전체 페이지 정보를 조회 메소드 정의 시작
     * @param page 요청 페이지 번호 (int, 1부터 시작) - 파라미터 설명
     * @param size 페이지당 게시글 수 (int) - 파라미터 설명
     * @param searchType [신규] 검색 타입 (String) - 파라미터 설명
     * @param searchKeyword [신규] 검색어 (String) - 파라미터 설명
     * @return Map (키: "posts", "totalPages", "totalItems", "currentPage") - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    // [신규] searchType, searchKeyword 파라미터 2개 추가
    public Map<String, Object> getAllPosts(int page, int size, String searchType, String searchKeyword) {
        // 1. offset 계산 (DB 건너뛸 개수). page는 1부터 시작
        int offset = (page - 1) * size; // offset 변수 계산 및 할당

        // 2. DAO findAll 호출하여 해당 페이지 게시글 목록 조회. limit(size), offset 전달
        // [신규] searchType, searchKeyword 파라미터를 DAO로 전달
        List<Map<String, Object>> posts = boardDAO.findAll(size, offset, searchType, searchKeyword); // posts 변수 초기화

        // 3. DAO countAll 호출하여 전체 게시글 수 조회
        // [신규] searchType, searchKeyword 파라미터를 DAO로 전달 (검색 조건에 맞는 전체 개수)
        int totalItems = boardDAO.countAll(searchType, searchKeyword); // totalItems 변수 초기화

        // 4. 전체 페이지 수 계산. Math.ceil() 메소드 사용하여 올림 처리
        int totalPages = (int) Math.ceil((double) totalItems / size); // totalPages 변수 계산 및 할당

        // 5. 결과를 담을 HashMap 객체 생성
        Map<String, Object> result = new HashMap<>(); // result 변수 초기화
        // result 맵에 .put() 메소드로 결과 데이터 저장 시작
        result.put("posts", posts);         // "posts" 키: 현재 페이지 게시글 목록
        result.put("totalItems", totalItems); // "totalItems" 키: 전체 게시글 수
        result.put("totalPages", totalPages);   // "totalPages" 키: 전체 페이지 수
        result.put("currentPage", page);     // "currentPage" 키: 요청된 현재 페이지 번호
        // result 맵에 .put() 메소드로 결과 데이터 저장 끝

        // 6. 완성된 result Map 반환
        return result;
    } // getAllPosts 메소드 끝

        /**
     * 게시글 생성 + 첨부파일 저장 메소드
     * @param post   제목/내용 등이 들어 있는 Map
     * @param files  업로드된 첨부파일 목록
     * @param userId 작성자 ID
     * @return 생성된 게시글 정보(Map). 실패 시 null.
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시 // ★ 수정됨
    public Map<String, Object> createPost(Map<String, Object> post, List<MultipartFile> files, String userId) { // ★ 수정됨: files 파라미터 추가
        // 1. 작성자 ID 설정
        post.put("user_id", userId); // 게시글 작성자 ID 설정

        // 2. DB에 게시글 INSERT
        int affectedRows = boardDAO.save(post); // boardDAO.save() 호출하여 DB에 저장 (post_id 생성)

        if (affectedRows <= 0) { // INSERT 실패
            return null;
        }

        // 3. 방금 INSERT된 게시글 ID 가져오기
        Object postIdObj = post.get("post_id"); // boardDAO.save()에서 생성된 PK가 넣어져 있다고 가정
        if (!(postIdObj instanceof Number)) {
            return null; // 안전 장치 (예외 상황)
        }
        int postId = ((Number) postIdObj).intValue();

        // 4. 첨부파일이 있다면 저장
        if (files != null && !files.isEmpty()) { // ★ 추가됨: 파일 저장 로직
            fileService.saveFilesForPost(postId, files); // 게시글 ID 기준으로 파일들 저장
        }

        // 5. 최종적으로 생성된 게시글 전체 정보(첨부파일 포함)를 다시 조회해서 반환
        return getPost(postId); // ★ 수정됨: post 대신 getPost(postId) 결과 반환
    }


    /**
     * 특정 게시글 조회 메소드 정의 시작
     * @param postId 조회할 게시글 ID (int) - 파라미터 설명
     * @return 게시글 정보 (Map) 또는 null - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public Map<String, Object> getPost(int postId) { // getPost 메소드 정의 시작
        // boardDAO.findById() 메소드 호출 (Optional<Map> 반환)
        // .orElse(null) 메소드: Optional 객체가 비어있으면(null) null 반환
        return boardDAO.findById(postId).orElse(null);
    } // getPost 메소드 끝

        /**
     * 게시글 수정 + 첨부파일 추가/삭제 메소드
     * @param postId        수정할 게시글 ID
     * @param postDetails   수정할 제목/내용 등이 들어있는 Map
     * @param newFiles      새로 추가할 파일 목록
     * @param deleteFileIds 삭제할 파일 ID 목록
     * @param currentUserId 현재 로그인 사용자 ID
     * @return 수정된 게시글 정보(Map). 권한 없음/실패 시 null.
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시 // ★ 수정됨
    public Map<String, Object> updatePost(
            int postId,
            Map<String, Object> postDetails,
            List<MultipartFile> newFiles,      // ★ 추가됨
            List<Integer> deleteFileIds,       // ★ 추가됨
            String currentUserId) {            // ★ 수정됨: 시그니처 변경

        // 1. 수정 대상 게시글 조회
        Map<String, Object> post = boardDAO.findById(postId).orElse(null); // post 변수 초기화

        // 2. 권한 확인 (게시글 존재 + 작성자 일치)
        if (post == null || !post.get("user_id").equals(currentUserId)) { // 게시글 없거나 작성자 아니면
            return null; // 권한 없음 또는 게시글 없음
        }

        // 3. 제목/내용 수정
        post.put("title", postDetails.get("title"));     // "title" 키 값 업데이트
        post.put("content", postDetails.get("content")); // "content" 키 값 업데이트

        // 4. DB에 게시글 UPDATE
        int affectedRows = boardDAO.update(post); // boardDAO.update() 메소드 호출

        if (affectedRows <= 0) {
            return null; // UPDATE 실패
        }

        // 5. 첨부파일 삭제 처리 (체크된 파일들)
        if (deleteFileIds != null && !deleteFileIds.isEmpty()) { // ★ 추가됨
            fileService.deleteFilesByIds(deleteFileIds); // 선택된 파일 ID들 삭제 // ★ 추가됨
        }

        // 6. 새로 추가된 첨부파일 저장
        if (newFiles != null && !newFiles.isEmpty()) { // ★ 추가됨
            fileService.saveFilesForPost(postId, newFiles); // 게시글 ID 기준으로 파일들 추가 저장 // ★ 추가됨
        }

        // 7. 수정 후 최신 게시글 정보(첨부파일 포함)를 다시 조회해서 반환
        return getPost(postId); // ★ 수정됨: post 대신 getPost(postId) 반환
    }


    /**
     * 게시글 삭제 메소드 정의 시작 (관리자 또는 작성자)
     * @param postId 삭제할 게시글 ID (int) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 파라미터 설명
     * @param roles 현재 사용자 역할 목록 (List<String>) - 파라미터 설명
     * @return 삭제 성공 여부 (boolean) - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
public boolean deletePost(int postId, String currentUserId, List<String> roles) { // deletePost 메소드 정의 시작
    // boardDAO.findById() 호출하여 삭제할 게시글 조회
    Map<String, Object> post = boardDAO.findById(postId).orElse(null); // post 변수 초기화

    // 권한 확인 로직 시작 (게시글 존재 여부 및 관리자 또는 작성자 일치 확인)
    if (post != null && (roles.contains("ADMIN") || post.get("user_id").equals(currentUserId))) { // if 시작

        // ★ 수정됨: 게시글 삭제 전에 첨부 파일 전체 삭제
        // 1) post_files 테이블에서 해당 post_id의 파일 메타데이터 전체 조회
        // 2) 실제 uploads 디렉토리에서 파일 삭제
        // 3) post_files 테이블 메타데이터 삭제
        fileService.deleteFilesByPostId(postId); // ★ 수정됨: 첨부파일 정리

        // 기존 로직: 게시글 자체 삭제
        return boardDAO.delete(postId) > 0;
    } // if 끝

    // 게시글 없거나 권한 없으면 false 반환
    return false;
} // deletePost 메소드 끝


    /**
     * [유지] 특정 게시글 조회수 증가 메소드 정의 시작
     * @param postId 조회수 증가시킬 게시글 ID (int) - 파라미터 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public void incrementViewCount(int postId) { // incrementViewCount 메소드 정의 시작
        // boardDAO.incrementViewCount() 메소드 호출
        boardDAO.incrementViewCount(postId);
    } // incrementViewCount 메소드 끝

    /**
     * [신규 추가] 게시글 고정 메소드 정의 시작 (관리자 전용)
     * @param postId 고정할 게시글 ID (int) - 파라미터 설명
     * @param order 고정 순서 (int, 예: 1, 2, 3) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 권한 확인용 파라미터 설명
     * @param roles 현재 사용자 역할 목록 (List<String>) - 권한 확인용 파라미터 설명
     * @return 고정 성공 여부 (boolean, 3개 제한 초과 시 false) - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public boolean pinPost(int postId, int order, String currentUserId, List<String> roles) { // pinPost 메소드 정의 시작
        // 1. 관리자 권한 확인 (roles 리스트에 "ADMIN" 포함 여부 확인)
        if (!roles.contains("ADMIN")) { // if 시작 (!는 부정)
            // 관리자 아니면 false 반환 (권한 없음)
            return false;
        } // if 끝

        // 2. 현재 고정된 게시글 수 확인 (boardDAO.countPinned() 호출)
        int currentPinnedCount = boardDAO.countPinned(); // currentPinnedCount 변수 초기화

        // 3. 고정 개수 3개 제한 확인
        if (currentPinnedCount >= 3) { // if 시작 (3개 이상이면)
            // System.out.println() 메소드: 서버 콘솔 로그 출력
            System.out.println("고정 개수 제한 초과: 최대 3개까지만 고정 가능.");
            // false 반환 (제한 초과)
            return false;
        } // if 끝

        // 4. 권한 있고 제한 개수 미만이면, 고정 작업 진행
        // boardDAO.updatePinnedOrder() 메소드 호출하여 DB 업데이트 요청
        int affectedRows = boardDAO.updatePinnedOrder(postId, order); // affectedRows 변수 초기화

        // 결과(영향받은 행 수)가 0보다 큰지 비교하여 boolean 값(true/false) 반환
        return affectedRows > 0;
    } // pinPost 메소드 끝

    /**
     * [신규 추가] 게시글 고정 해제 메소드 정의 시작 (관리자 전용)
     * @param postId 고정 해제할 게시글 ID (int) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 권한 확인용 파라미터 설명
     * @param roles 현재 사용자 역할 목록 (List<String>) - 권한 확인용 파라미터 설명
     * @return 고정 해제 성공 여부 (boolean) - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public boolean unpinPost(int postId, String currentUserId, List<String> roles) { // unpinPost 메소드 정의 시작
        // 1. 관리자 권한 확인
        if (!roles.contains("ADMIN")) { // if 시작
            // 관리자 아니면 false 반환
            return false;
        } // if 끝

        // 2. 고정 해제 작업 진행 (pinned_order 값을 null로 업데이트)
        // boardDAO.updatePinnedOrder() 메소드 호출 (order 파라미터에 null 전달)
        int affectedRows = boardDAO.updatePinnedOrder(postId, null); // affectedRows 변수 초기화

        // 결과(영향받은 행 수)가 0보다 큰지 비교하여 boolean 값(true/false) 반환
        return affectedRows > 0;
    } // unpinPost 메소드 끝

} // BoardServiceImpl 클래스 정의 끝