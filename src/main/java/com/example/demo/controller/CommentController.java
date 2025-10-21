package com.example.demo.controller;

import com.example.demo.service.comment.ICommentService;
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입
import org.springframework.http.ResponseEntity; // http 응답 세밀한 제어
import org.springframework.security.core.Authentication; // 현재 로그인한 사용자 인증 정보 담는 도구
import org.springframework.security.core.GrantedAuthority; // 사용자 권한 정보 담는 도구
import org.springframework.web.bind.annotation.*; // 모든 관련 어노테이션 한 번에 import
import java.util.List; // 여러 데이터 목록 형태 다루기 위한 도구
import java.util.Map; // 데이터 이름-값 쌍으로 다루기 위한 도구
import java.util.stream.Collectors; // 컬렉션 데이터 처리 도구

@RestController
public class CommentController {

    @Autowired
    private ICommentService commentService;

    // 특정 게시글의 모든 댓글 조회 API
    // @GetMapping("/api/posts/{postId}/comments"): HTTP GET 방식으로 '/api/posts/1/comments' 와 같은 주소에 요청이 오면 이 메소드를 실행
    @GetMapping("/api/posts/{postId}/comments")
    //@PathVariable int postId: URL 경로의 {postId} 부분을 정수(int) 타입의 postId 변수에 담음
    public List<Map<String, Object>> getComments(@PathVariable int postId) {
        //commentService에게 postId를 전달하여 해당 게시글의 모든 댓글을 찾아달라고 요청하고, 받은 결과를 그대로 브라우저에 반환
        return commentService.getCommentsByPostId(postId);
    }

    // 특정 게시글에 새 댓글 작성 API
    @PostMapping("/api/posts/{postId}/comments")
    //ResponseEntity: HTTP 상태 코드(200, 500 등)와 응답 데이터를 함께 제어할 수 있는 객체
    public ResponseEntity<Map<String, Object>> createComment(@PathVariable int postId, @RequestBody Map<String, Object> comment, Authentication authentication) {
        // @RequestBody Map<String, Object> comment: 요청의 본문(body)에 담겨온 JSON 데이터를 Map 형태로 변환
        // Authentication authentication: 현재 로그인한 사용자의 인증 정보를 담고 있는 객체
        // authentication.getName(): 현재 로그인한 사용자의 ID를 가져옴
        String currentUserId = authentication.getName();
        // commentService의 댓글 생성 로직 수행, 결과 createdComment에 저장
        Map<String, Object> createdComment = commentService.createComment(postId, comment, currentUserId);
        
        //생성 실패 시, 서버 에러를 응답
        // createdComment가 null이 아니다? (댓글 생성 성공)
        if (createdComment != null) {
            // 성공 응답(200 OK)과 생성된 댓글 데이터 반환
            return ResponseEntity.ok(createdComment);
        } else { // 생성 실패 시
            // '500 Internal Server Error' 상태 응답 반환
            return ResponseEntity.internalServerError().build(); // 500 Internal Server Error
        }
    }

    // 특정 댓글 수정 API
    @PutMapping("/api/comments/{commentId}")
    // @PathVariable int commentId: URL의 {commentId} 부분을 int 타입의 commentId 변수에 담음
    public ResponseEntity<Map<String, Object>> updateComment(@PathVariable int commentId, @RequestBody Map<String, Object> commentDetails, Authentication authentication) {
        // Authentication authentication: 현재 로그인한 사용자의 인증 정보를 담고 있는 객체
        // authentication.getName(): 현재 로그인한 사용자의 ID를 가져옴
        String currentUserId = authentication.getName();
        // commentService의 댓글 수정 로직 수행, 결과 updatedComment에 저장
        Map<String, Object> updatedComment = commentService.updateComment(commentId, commentDetails, currentUserId);
        if (updatedComment != null) {
            // 성공 응답(200 OK)과 수정된 댓글 데이터 반환
            return ResponseEntity.ok(updatedComment);
            // 수정 실패 시
        } else {
        // '403 Forbidden' 상태 응답 반환 (수정 권한 없음)
            return ResponseEntity.status(403).build(); // Forbidden
        }
    }

    // 특정 댓글 삭제 API
    @DeleteMapping("/api/comments/{commentId}")
    // @PathVariable int commentId: URL의 {commentId} 부분을 int 타입의 commentId 변수에 담음
    public ResponseEntity<Void> deleteComment(@PathVariable int commentId, Authentication authentication) {
        // Authentication authentication: 현재 로그인한 사용자의 인증 정보를 담고 있는 객체
        // authentication.getName(): 현재 로그인한 사용자의 ID를 가져옴
        String currentUserId = authentication.getName();
        //현재 로그인 중인 사용자 역할 LIST형태로 가져옴
        List<String> roles = authentication.getAuthorities().stream()
                // GrantedAuthority::getAuthority(): 각 권한 객체에서 권한 이름(문자열) 추출
                .map(GrantedAuthority::getAuthority)
                // .replace("ROLE_", ""): "ROLE_" 접두어 제거 (예: "ROLE_ADMIN" -> "ADMIN")
                .map(auth -> auth.replace("ROLE_", ""))
                // Collectors.toList(): 스트림의 모든 요소를 리스트로 수집
                .collect(Collectors.toList());
        //commentService에 삭제할 댓글 ID 요청자 ID 요청자 역할 목록 전달
        boolean isDeleted = commentService.deleteComment(commentId, currentUserId, roles);
        if (isDeleted) {// 삭제 성공
            return ResponseEntity.ok().build(); // 200 OK
        } else { // 삭제 실패 시
            return ResponseEntity.status(403).build(); // '403 Forbidden' 상태 응답 반환 (삭제 권한 없음)
        }
    }
}