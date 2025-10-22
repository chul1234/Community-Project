package com.example.demo.controller;

import com.example.demo.service.board.IBoardService;// 게시판 서비스 인터페이스 import
import org.springframework.beans.factory.annotation.Autowired; //spring에 의존성 주입 기능
import org.springframework.http.ResponseEntity; //HTTP응답 세밀하게 제어
import org.springframework.security.core.Authentication; //현재 로그인한 사용자 인증 정보담는 도구
import org.springframework.security.core.GrantedAuthority; //사용자 권한 정보 담는 도구
import org.springframework.web.bind.annotation.*; // 모든 관련 어노테이션을 한 번에 import

import java.util.List; //여러 데이터 목록 형태 다루기 위한 도구
import java.util.Map; // 데이터 이름 -값 쌍으로 다루기 위한 도구
import java.util.stream.Collectors; //컬렉션 데이터 처리 도구

// json 같은 순수 데이터로 응답하는 컨트롤러
@RestController
public class BoardController {

    // @Autowired: Spring이 미리 만들어 둔 IBoardService 타입의 객체
    @Autowired
    private IBoardService boardService; // IBoardService 주입

    // @GetMapping("/api/posts"): HTTP GET 방식으로 '/api/posts' 주소에 요청 실행
    @GetMapping("/api/posts") // 모든 게시글 조회 API
    public List<Map<String, Object>> getAllPosts() {
        //boardService에게 모든 게시글 찾아달라고 요청, 결과 웹에 반환
        // 이제 DAO가 아닌 Service를 통해 데이터를 조회합니다.
        return boardService.getAllPosts(); // boardService.getAllPosts() 호출
    }

    //HTTP POST 방식으로 '/api/posts' 주소에 요청이 오면 이 메소드를 실행
    @PostMapping("/api/posts")
    //ResponseEntity: HTTP 상태 코드(200, 404 등)와 응답 데이터를 함께 제어
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Map<String, Object> post, Authentication authentication) {
        //RequestBody MAp<String, Object> post : 프론트엔드가 보낸 요청의 본문 json데이터를 map형태로 변환 post에 변수에 데이터 담음
        //Authentication authentication : 현재 로그인한 사용자의 인증 정보를 담고 있는 객체

        //authentication.getName(): 현재 로그인한 사용자의 ID를 가져옴
        String userId = authentication.getName();
        //boardService의 게시글 데이터 , 작성자 ID 절달하여 생성 로직 수행, 결과 createdPost 저장
        Map<String, Object> createdPost = boardService.createPost(post, userId); // boardService.createPost() 호출
        //cratePost가 null이 아니다? (게시글 생성 성공)
        if (createdPost != null) {
            // 성공 응답(200 OK)과 생성된 게시글 데이터 반환
            return ResponseEntity.ok(createdPost);
            // 생성 실패 시
        } else {
            //ResponseEntity.internalServerError().build(): '500 Internal Server Error' (서버 내부 오류) 상태를 응답
            return ResponseEntity.internalServerError().build();
        }
    }

    //'/api/posts/1' 처럼 특정 게시글 ID가 포함된 GET 요청 처리
    @GetMapping("/api/posts/{postId}")
    // @PathVariable int postId: URL의 {postId} 부분을 int 타입의 postId 변수에 담음
    public ResponseEntity<Map<String, Object>> getPostById(@PathVariable int postId) {

        // [수정] 게시글 정보를 가져오기 전에 조회수를 먼저 증가시킵니다.
        boardService.incrementViewCount(postId); // boardService.incrementViewCount() 호출

        // boardService에게 해당 postId의 게시글 찾아달라고 요청, 결과 post에 저장
        Map<String, Object> post = boardService.getPost(postId); // boardService.getPost() 호출
        //post가 null이 아니다? (게시글 존재)
        if (post != null) {
            // 성공 응답(200 OK)과 게시글 데이터 반환
            return ResponseEntity.ok(post);
        } else { // 게시글이 없으면
            // '404 Not Found' 상태 응답 반환
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/api/posts/{postId}") // HTTP PUT 방식으로 '/api/posts/{postId}' 주소에 요청이 오면 이 메소드를 실행
    public ResponseEntity<Map<String, Object>> updatePost(@PathVariable int postId, @RequestBody Map<String, Object> postDetails, Authentication authentication) {
        // Authentication authentication: 현재 로그인한 사용자의 인증 정보를 담고 있는 객체
        String currentUserId = authentication.getName();
        // 관리자도 수정 가능하도록 로직 추가 (선택 사항이지만 좋은 개선입니다)
        // List<String> roles = authentication.getAuthorities().stream()
        //         .map(GrantedAuthority::getAuthority)
        //         .map(auth -> auth.replace("ROLE_", ""))
        //         .collect(Collectors.toList());

        // updatePost 서비스 메소드는 아직 역할을 받도록 수정되지 않았지만, 향후 확장을 위해 미리 구조를 잡아둡니다.
        // 현재는 작성자 본인만 수정 가능합니다.
        Map<String, Object> updatedPost = boardService.updatePost(postId, postDetails, currentUserId); // boardService.updatePost() 호출
        // 업데이트된 게시글이 null이 아니다? (수정 성공)
        if (updatedPost != null) {
            // 성공 응답(200 OK)과 수정된 게시글 데이터 반환
            return ResponseEntity.ok(updatedPost);
        } else {// 수정 실패 시
            // '403 Forbidden' 상태 응답 반환 (수정 권한 없음)
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/api/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable int postId, Authentication authentication) {
        // Authentication authentication: 현재 로그인한 사용자의 인증 정보를 담고 있는 객체
        String currentUserId = authentication.getName();
        //현재 로그인 중인 사용자 역할 LIST형태로 가져옴
        //스트림 API 사용
        List<String> roles = authentication.getAuthorities().stream()
                // GrantedAuthority::getAuthority(): 각 권한 객체에서 권한 이름(문자열) 추출
                .map(GrantedAuthority::getAuthority)
                // .replace("ROLE_", ""): "ROLE_" 접두어 제거 (예: "ROLE_ADMIN" -> "ADMIN")
                .map(auth -> auth.replace("ROLE_", ""))
                // Collectors.toList(): 스트림의 모든 요소를 리스트로 수집
                .collect(Collectors.toList());
        //boardService에 삭제할 게시글 ID 요청자 ID 요청자 역할 목록 전달
        //관리자 이거나 작성자 본인일 때 삭제 허용
        boolean isDeleted = boardService.deletePost(postId, currentUserId, roles); // boardService.deletePost() 호출
        //삭제 성공 시
        if (isDeleted) { // 삭제 성공
            // '200 OK' 상태 응답 반환
            return ResponseEntity.ok().build();
        } else { // 삭제 실패 시
            // '403 Forbidden' 상태 응답 반환 (삭제 권한 없음)
            return ResponseEntity.status(403).build();
        }
    }
}