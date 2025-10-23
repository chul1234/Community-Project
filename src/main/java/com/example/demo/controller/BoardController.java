package com.example.demo.controller; // 패키지 선언

// 필요한 클래스 import
import com.example.demo.service.board.IBoardService; // IBoardService 인터페이스 import
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 어노테이션 import
import org.springframework.http.ResponseEntity; // ResponseEntity 클래스 import (HTTP 응답 제어)
import org.springframework.security.core.Authentication; // Authentication 인터페이스 import (인증 정보)
import org.springframework.security.core.GrantedAuthority; // GrantedAuthority 인터페이스 import (권한 정보)
import org.springframework.web.bind.annotation.*; // Spring Web 어노테이션들 import (@RestController, @GetMapping 등)

import java.util.List; // List 인터페이스 import
import java.util.Map; // Map 인터페이스 import
import java.util.stream.Collectors; // Collectors 클래스 import (Stream API)

// json 같은 순수 데이터로 응답하는 컨트롤러 (@RestController = @Controller + @ResponseBody)
@RestController
public class BoardController { // BoardController 클래스 정의 시작

    // @Autowired: Spring이 IBoardService 타입 Bean 자동 주입
    @Autowired
    private IBoardService boardService; // IBoardService 객체 멤버 변수 선언

    /**
     * [유지] 페이지네이션 적용된 게시글 목록 조회 API 메소드 정의 시작
     * @param page 요청 페이지 번호 (int, 기본값 1) - 파라미터 설명
     * @param size 페이지당 게시글 수 (int, 기본값 10) - 파라미터 설명
     * @return Map (키: "posts", "totalPages", "totalItems", "currentPage") - 반환 타입 설명
     */
    // @GetMapping("/api/posts"): HTTP GET /api/posts 요청 처리
    @GetMapping("/api/posts")
    public Map<String, Object> getAllPosts( // getAllPosts 메소드 정의 시작
            // @RequestParam: URL 쿼리 파라미터 (?page=...&size=...) 값 받음
            // defaultValue: 파라미터 없을 시 기본값 설정
            @RequestParam(defaultValue = "1") int page, // page 파라미터 (int)
            @RequestParam(defaultValue = "10") int size // size 파라미터 (int)
    ) { // getAllPosts 메소드 파라미터 끝
        // [유지] boardService.getAllPosts() 호출 시 page, size 전달
        return boardService.getAllPosts(page, size); // 결과 Map 반환
    } // getAllPosts 메소드 끝

    /**
     * 게시글 생성 API 메소드 정의 시작
     * @param post 요청 본문 JSON 데이터 (Map) - 파라미터 설명
     * @param authentication 현재 인증 정보 (Authentication) - 파라미터 설명
     * @return ResponseEntity<Map<String, Object>> - 생성된 게시글 정보 또는 에러 상태 응답 - 반환 타입 설명
     */
    // @PostMapping("/api/posts"): HTTP POST /api/posts 요청 처리
    @PostMapping("/api/posts")
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Map<String, Object> post, Authentication authentication) { // createPost 메소드 정의 시작
        // @RequestBody: 요청 본문(body) JSON 데이터를 Map<String, Object> 타입으로 자동 변환하여 post 파라미터에 주입
        // Authentication: Spring Security가 자동으로 현재 사용자 인증 정보 주입

        // authentication.getName() 메소드: 현재 로그인 사용자 ID(username) 반환
        String userId = authentication.getName(); // userId 변수 초기화
        // boardService.createPost() 메소드 호출하여 게시글 생성 로직 수행
        Map<String, Object> createdPost = boardService.createPost(post, userId); // createdPost 변수 초기화

        // createdPost null 여부 확인 (null이면 생성 실패)
        if (createdPost != null) { // if 시작 (생성 성공)
            // ResponseEntity.ok() 메소드: HTTP 200 OK 상태와 응답 본문(createdPost) 설정하여 반환
            return ResponseEntity.ok(createdPost);
        } else { // else 시작 (생성 실패)
            // ResponseEntity.internalServerError().build() 메소드: HTTP 500 Internal Server Error 상태 응답 반환 (본문 없음)
            return ResponseEntity.internalServerError().build();
        } // if-else 끝
    } // createPost 메소드 끝

    /**
     * 특정 게시글 상세 조회 API 메소드 정의 시작
     * @param postId URL 경로에서 추출한 게시글 ID (int) - 파라미터 설명
     * @return ResponseEntity<Map<String, Object>> - 게시글 정보 또는 404 Not Found 응답 - 반환 타입 설명
     */
    // @GetMapping("/api/posts/{postId}"): HTTP GET /api/posts/{postId} 요청 처리
    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<Map<String, Object>> getPostById(@PathVariable int postId) { // getPostById 메소드 정의 시작
        // @PathVariable: URL 경로 변수({postId}) 값을 메소드 파라미터(postId)에 주입

        // [유지] boardService.incrementViewCount() 호출하여 조회수 증가
        boardService.incrementViewCount(postId);

        // boardService.getPost() 호출하여 게시글 정보 조회
        Map<String, Object> post = boardService.getPost(postId); // post 변수 초기화

        // post null 여부 확인 (null이면 게시글 없음)
        if (post != null) { // if 시작 (게시글 존재)
            // ResponseEntity.ok() 메소드: HTTP 200 OK 상태와 응답 본문(post) 설정하여 반환
            return ResponseEntity.ok(post);
        } else { // else 시작 (게시글 없음)
            // ResponseEntity.notFound().build() 메소드: HTTP 404 Not Found 상태 응답 반환
            return ResponseEntity.notFound().build();
        } // if-else 끝
    } // getPostById 메소드 끝

    /**
     * 게시글 수정 API 메소드 정의 시작
     * @param postId URL 경로에서 추출한 게시글 ID (int) - 파라미터 설명
     * @param postDetails 요청 본문 JSON 데이터 (수정 내용 Map) - 파라미터 설명
     * @param authentication 현재 인증 정보 (Authentication) - 파라미터 설명
     * @return ResponseEntity<Map<String, Object>> - 수정된 게시글 정보 또는 403 Forbidden 응답 - 반환 타입 설명
     */
    // @PutMapping("/api/posts/{postId}"): HTTP PUT /api/posts/{postId} 요청 처리
    @PutMapping("/api/posts/{postId}")
    public ResponseEntity<Map<String, Object>> updatePost(@PathVariable int postId, @RequestBody Map<String, Object> postDetails, Authentication authentication) { // updatePost 메소드 정의 시작
        // authentication.getName() 메소드: 현재 사용자 ID 획득
        String currentUserId = authentication.getName(); // currentUserId 변수 초기화

        // boardService.updatePost() 호출하여 게시글 수정 로직 수행
        Map<String, Object> updatedPost = boardService.updatePost(postId, postDetails, currentUserId); // updatedPost 변수 초기화

        // updatedPost null 여부 확인 (null이면 수정 실패 또는 권한 없음)
        if (updatedPost != null) { // if 시작 (수정 성공)
            // ResponseEntity.ok() 메소드: HTTP 200 OK 상태와 응답 본문(updatedPost) 설정하여 반환
            return ResponseEntity.ok(updatedPost);
        } else { // else 시작 (수정 실패)
            // ResponseEntity.status(403).build() 메소드: HTTP 403 Forbidden 상태 응답 반환
            return ResponseEntity.status(403).build();
        } // if-else 끝
    } // updatePost 메소드 끝

    /**
     * 게시글 삭제 API 메소드 정의 시작 (관리자 또는 작성자)
     * @param postId URL 경로에서 추출한 게시글 ID (int) - 파라미터 설명
     * @param authentication 현재 인증 정보 (Authentication) - 파라미터 설명
     * @return ResponseEntity<Void> - 성공(200 OK) 또는 실패(403 Forbidden) 상태 응답 - 반환 타입 설명
     */
    // @DeleteMapping("/api/posts/{postId}"): HTTP DELETE /api/posts/{postId} 요청 처리
    @DeleteMapping("/api/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable int postId, Authentication authentication) { // deletePost 메소드 정의 시작
        // authentication.getName() 메소드: 현재 사용자 ID 획득
        String currentUserId = authentication.getName(); // currentUserId 변수 초기화
        // authentication.getAuthorities() 메소드: 현재 사용자 권한 목록(GrantedAuthority) 반환
        // .stream() 메소드: 권한 목록 스트림 생성
        List<String> roles = authentication.getAuthorities().stream() // roles 변수 초기화 시작
                // .map(메소드 레퍼런스): 각 GrantedAuthority 객체에서 권한 이름(String) 추출
                .map(GrantedAuthority::getAuthority) // 예: "ROLE_ADMIN"
                // .map(람다식): 각 권한 이름 문자열에서 "ROLE_" 접두어 제거
                .map(auth -> auth.replace("ROLE_", "")) // 예: "ADMIN"
                // .collect(Collectors.toList()): 스트림 결과 List<String>으로 수집
                .collect(Collectors.toList()); // roles 변수 초기화 끝

        // boardService.deletePost() 호출하여 게시글 삭제 로직 수행 (권한 확인 포함)
        boolean isDeleted = boardService.deletePost(postId, currentUserId, roles); // isDeleted 변수 초기화

        // isDeleted 값 확인 (true면 삭제 성공)
        if (isDeleted) { // if 시작 (삭제 성공)
            // ResponseEntity.ok().build() 메소드: HTTP 200 OK 상태 응답 반환 (본문 없음)
            return ResponseEntity.ok().build();
        } else { // else 시작 (삭제 실패 또는 권한 없음)
            // ResponseEntity.status(403).build() 메소드: HTTP 403 Forbidden 상태 응답 반환
            return ResponseEntity.status(403).build();
        } // if-else 끝
    } // deletePost 메소드 끝

    /**
     * [신규 추가됨] 게시글 고정 API 메소드 정의 시작 (관리자 전용)
     * @param postId URL 경로에서 추출한 게시글 ID (int) - 파라미터 설명
     * @param requestBody 요청 본문 JSON 데이터 (Map, 예: { "order": 1 }) - 파라미터 설명
     * @param authentication 현재 인증 정보 (Authentication) - 파라미터 설명
     * @return ResponseEntity<Void> - 성공(200 OK), 실패(400 Bad Request - 제한 초과), 실패(403 Forbidden - 권한 없음) 응답 - 반환 타입 설명
     */
    // @PutMapping("/api/posts/{postId}/pin"): HTTP PUT /api/posts/{postId}/pin 요청 처리
    // @PreAuthorize("hasRole('ADMIN')"): 이 메소드는 ADMIN 역할 사용자만 호출 가능 (Spring Security Method Security 필요)
    // @PreAuthorize("hasRole('ADMIN')") // <- 주석 해제하여 활성화 
    @PutMapping("/api/posts/{postId}/pin")
    public ResponseEntity<Void> pinPost(@PathVariable int postId, @RequestBody Map<String, Integer> requestBody, Authentication authentication) { // pinPost 메소드 정의 시작
        // authentication.getName() 메소드: 현재 사용자 ID 획득
        String currentUserId = authentication.getName(); // currentUserId 변수 초기화
        // authentication.getAuthorities() ~ .collect(Collectors.toList()): 현재 사용자 역할 목록(List<String>) 추출
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList()); // roles 변수 초기화

        // requestBody.get("order"): 요청 본문에서 "order" 키 값(Integer) 추출
        Integer order = requestBody.get("order"); // order 변수 초기화
        // order 값 유효성 확인 (null이 아니어야 함)
        if (order == null) { // if 시작 (order 값 없음)
            // ResponseEntity.badRequest().build(): HTTP 400 Bad Request 상태 응답 반환
            return ResponseEntity.badRequest().build(); // 잘못된 요청 처리
        } // if 끝

        // boardService.pinPost() 호출하여 게시글 고정 로직 수행 (권한 확인 및 3개 제한 포함)
        boolean success = boardService.pinPost(postId, order, currentUserId, roles); // success 변수 초기화

        // success 값 확인
        if (success) { // if 시작 (고정 성공)
            // ResponseEntity.ok().build(): HTTP 200 OK 상태 응답 반환
            return ResponseEntity.ok().build();
        } else { // else 시작 (고정 실패: 권한 없거나 3개 제한 초과)
            // 여기서 권한 문제인지 제한 문제인지 구분 필요 시 Service 반환값 수정 필요 (현재는 boolean)
            // 일단 실패 시 403 Forbidden 반환 (권한 문제일 수 있으므로)
            return ResponseEntity.status(403).build();
        } // if-else 끝
    } // pinPost 메소드 끝

    /**
     * [신규 추가됨] 게시글 고정 해제 API 메소드 정의 시작 (관리자 전용)
     * @param postId URL 경로에서 추출한 게시글 ID (int) - 파라미터 설명
     * @param authentication 현재 인증 정보 (Authentication) - 파라미터 설명
     * @return ResponseEntity<Void> - 성공(200 OK) 또는 실패(403 Forbidden - 권한 없음) 응답 - 반환 타입 설명
     */
    // @PutMapping("/api/posts/{postId}/unpin"): HTTP PUT /api/posts/{postId}/unpin 요청 처리
    // @PreAuthorize("hasRole('ADMIN')") // <- 주석 해제하여 활성화 추천
    @PutMapping("/api/posts/{postId}/unpin")
    public ResponseEntity<Void> unpinPost(@PathVariable int postId, Authentication authentication) { // unpinPost 메소드 정의 시작
        // authentication.getName() 메소드: 현재 사용자 ID 획득
        String currentUserId = authentication.getName(); // currentUserId 변수 초기화
        // authentication.getAuthorities() ~ .collect(Collectors.toList()): 현재 사용자 역할 목록(List<String>) 추출
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList()); // roles 변수 초기화

        // boardService.unpinPost() 호출하여 게시글 고정 해제 로직 수행 (권한 확인 포함)
        boolean success = boardService.unpinPost(postId, currentUserId, roles); // success 변수 초기화

        // success 값 확인
        if (success) { // if 시작 (해제 성공)
            // ResponseEntity.ok().build(): HTTP 200 OK 상태 응답 반환
            return ResponseEntity.ok().build();
        } else { // else 시작 (해제 실패: 권한 없음)
            // ResponseEntity.status(403).build(): HTTP 403 Forbidden 상태 응답 반환
            return ResponseEntity.status(403).build();
        } // if-else 끝
    } // unpinPost 메소드 끝

} // BoardController 클래스 정의 끝