package com.example.demo.controller;

import com.example.demo.service.comment.ICommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class CommentController {

    @Autowired
    private ICommentService commentService;

    // 특정 게시글의 모든 댓글 조회 API
    @GetMapping("/api/posts/{postId}/comments")
    public List<Map<String, Object>> getComments(@PathVariable int postId) {
        return commentService.getCommentsByPostId(postId);
    }

    // 특정 게시글에 새 댓글 작성 API
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> createComment(@PathVariable int postId, @RequestBody Map<String, Object> comment, Authentication authentication) {
        String currentUserId = authentication.getName();
        Map<String, Object> createdComment = commentService.createComment(postId, comment, currentUserId);
        
        // ▼▼▼▼▼ [수정] 생성 실패 시, 서버 에러를 응답하도록 변경 ▼▼▼▼▼
        if (createdComment != null) {
            return ResponseEntity.ok(createdComment);
        } else {
            return ResponseEntity.internalServerError().build(); // 500 Internal Server Error
        }
    }

    // 특정 댓글 수정 API
    @PutMapping("/api/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> updateComment(@PathVariable int commentId, @RequestBody Map<String, Object> commentDetails, Authentication authentication) {
        String currentUserId = authentication.getName();
        Map<String, Object> updatedComment = commentService.updateComment(commentId, commentDetails, currentUserId);
        if (updatedComment != null) {
            return ResponseEntity.ok(updatedComment);
        } else {
            return ResponseEntity.status(403).build(); // Forbidden
        }
    }

    // 특정 댓글 삭제 API
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable int commentId, Authentication authentication) {
        String currentUserId = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());
        
        boolean isDeleted = commentService.deleteComment(commentId, currentUserId, roles);
        if (isDeleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).build(); // Forbidden
        }
    }
}