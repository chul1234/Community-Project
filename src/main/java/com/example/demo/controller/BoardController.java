package com.example.demo.controller;

import com.example.demo.service.board.IBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*; // [수정] 모든 관련 어노테이션을 한 번에 import

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class BoardController {

    // BoardDAO에 대한 직접적인 의존성을 제거합니다.
    @Autowired
    private IBoardService boardService;

    @GetMapping("/api/posts")
    public List<Map<String, Object>> getAllPosts() {
        // 이제 DAO가 아닌 Service를 통해 데이터를 조회합니다.
        return boardService.getAllPosts();
    }

    @PostMapping("/api/posts")
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Map<String, Object> post, Authentication authentication) {
        String userId = authentication.getName();
        Map<String, Object> createdPost = boardService.createPost(post, userId);
        if (createdPost != null) {
            return ResponseEntity.ok(createdPost);
        } else {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<Map<String, Object>> getPostById(@PathVariable int postId) {
        Map<String, Object> post = boardService.getPost(postId);
        if (post != null) {
            return ResponseEntity.ok(post);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // @PutMapping("/api/posts/{postId}")
    // public ResponseEntity<Map<String, Object>> updatePost(@PathVariable int postId, @RequestBody Map<String, Object> postDetails, Authentication authentication) {
    //     String currentUserId = authentication.getName();
    //     // 관리자도 수정 가능하도록 로직 추가 (선택 사항이지만 좋은 개선입니다)
    //     List<String> roles = authentication.getAuthorities().stream()
    //             .map(GrantedAuthority::getAuthority)
    //             .map(auth -> auth.replace("ROLE_", ""))
    //             .collect(Collectors.toList());
        
    //     // updatePost 서비스 메소드는 아직 역할을 받도록 수정되지 않았지만, 향후 확장을 위해 미리 구조를 잡아둡니다.
    //     // 현재는 작성자 본인만 수정 가능합니다.
    //     Map<String, Object> updatedPost = boardService.updatePost(postId, postDetails, currentUserId);
        
    //     if (updatedPost != null) {
    //         return ResponseEntity.ok(updatedPost);
    //     } else {
    //         return ResponseEntity.status(403).build();
    //     }
    // }

    @DeleteMapping("/api/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable int postId, Authentication authentication) {
        String currentUserId = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());
        boolean isDeleted = boardService.deletePost(postId, currentUserId, roles);
        if (isDeleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).build();
        }
    }
}