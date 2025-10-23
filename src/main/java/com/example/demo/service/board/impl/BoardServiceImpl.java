package com.example.demo.service.board.impl; // 패키지 선언

// 필요한 클래스 import
import com.example.demo.dao.BoardDAO; // BoardDAO 클래스 import
import com.example.demo.service.board.IBoardService; // IBoardService 인터페이스 import
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 어노테이션 import
import org.springframework.stereotype.Service; // @Service 어노테이션 import

import java.util.HashMap; // HashMap 클래스 import
import java.util.Map; // Map 인터페이스 import
import java.util.List; // List 인터페이스 import

@Service // @Service: 서비스 계층 컴포넌트 선언
public class BoardServiceImpl implements IBoardService { // BoardServiceImpl 클래스 정의 시작, IBoardService 구현

    @Autowired // @Autowired: Spring이 BoardDAO Bean 자동 주입
    private BoardDAO boardDAO; // BoardDAO 객체 멤버 변수 선언

    /**
     * [유지] 특정 페이지의 게시글 목록과 전체 페이지 정보를 조회 메소드 정의 시작
     * @param page 요청 페이지 번호 (int, 1부터 시작) - 파라미터 설명
     * @param size 페이지당 게시글 수 (int) - 파라미터 설명
     * @return Map (키: "posts", "totalPages", "totalItems", "currentPage") - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public Map<String, Object> getAllPosts(int page, int size) { // getAllPosts 메소드 정의 시작
        // 1. offset 계산 (DB 건너뛸 개수). page는 1부터 시작
        int offset = (page - 1) * size; // offset 변수 계산 및 할당

        // 2. DAO findAll 호출하여 해당 페이지 게시글 목록 조회. limit(size), offset 전달
        List<Map<String, Object>> posts = boardDAO.findAll(size, offset); // posts 변수 초기화

        // 3. DAO countAll 호출하여 전체 게시글 수 조회
        int totalItems = boardDAO.countAll(); // totalItems 변수 초기화

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
     * 게시글 생성 메소드 정의 시작
     * @param post 생성할 게시글 정보 (Map) - 파라미터 설명
     * @param userId 작성자 ID (String) - 파라미터 설명
     * @return 생성된 게시글 정보 (Map) 또는 null - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public Map<String, Object> createPost(Map<String, Object> post, String userId) { // createPost 메소드 정의 시작
        // 1. post 맵에 .put() 메소드로 'user_id' 키와 userId 값 추가
        post.put("user_id", userId); // user_id 설정

        // 2. boardDAO.save() 메소드 호출하여 DB 저장 요청
        int affectedRows = boardDAO.save(post); // affectedRows 변수 초기화 (영향받은 행 수)

        // 3. affectedRows 값 확인 (0보다 크면 성공)
        if (affectedRows > 0) { // if 시작
            // 원본 게시글 데이터(post 맵) 컨트롤러 반환
            return post;
        } else { // else 시작
            // null 반환하여 컨트롤러에게 실패 알림
            return null;
        } // if-else 끝
    } // createPost 메소드 끝

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
     * 게시글 수정 메소드 정의 시작
     * @param postId 수정할 게시글 ID (int) - 파라미터 설명
     * @param postDetails 수정할 내용 (Map) - 파라미터 설명
     * @param currentUserId 현재 사용자 ID (String) - 파라미터 설명
     * @return 수정된 게시글 정보 (Map) 또는 null (권한 없음 등) - 반환 타입 설명
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public Map<String, Object> updatePost(int postId, Map<String, Object> postDetails, String currentUserId) { // updatePost 메소드 정의 시작
        // 1. boardDAO.findById() 호출하여 수정할 게시글 조회
        Map<String, Object> post = boardDAO.findById(postId).orElse(null); // post 변수 초기화

        // 2. 권한 확인 로직 시작 (게시글 존재 여부 및 작성자 일치 확인)
        // post != null: 게시글 존재 확인
        // post.get("user_id"): 게시글의 user_id 값 가져오기
        // .equals(currentUserId): 현재 사용자와 작성자 ID 비교
        if (post != null && post.get("user_id").equals(currentUserId)) { // if 시작
            // 3. 작성자 맞으면, post 맵 데이터 업데이트 시작 (.put() 메소드 사용)
            post.put("title", postDetails.get("title")); // "title" 키 값 업데이트
            post.put("content", postDetails.get("content")); // "content" 키 값 업데이트
            // 3. 작성자 맞으면, post 맵 데이터 업데이트 끝

            // 4. boardDAO.update() 메소드 호출하여 DB 업데이트 요청
            int affectedRows = boardDAO.update(post); // affectedRows 변수 초기화
            // 삼항 연산자: affectedRows > 0 이 true면 post 반환, false면 null 반환
            return affectedRows > 0 ? post : null;
        } // if 끝
        // 게시글 없거나 작성자 아니면 null 반환
        return null;
    } // updatePost 메소드 끝

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
        // post != null: 게시글 존재 확인
        // roles.contains("ADMIN"): 사용자 역할 목록에 "ADMIN" 포함 확인
        // post.get("user_id").equals(currentUserId): 작성자와 현재 사용자 ID 비교
        if (post != null && (roles.contains("ADMIN") || post.get("user_id").equals(currentUserId))) { // if 시작
            // 권한 있으면, boardDAO.delete() 메소드 호출하여 DB 삭제 요청
            // 결과(영향받은 행 수)가 0보다 큰지 비교하여 boolean 값(true/false) 반환
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