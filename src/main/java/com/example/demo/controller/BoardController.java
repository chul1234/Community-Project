package com.example.demo.controller;

import com.example.demo.dao.BoardDAO;
import com.example.demo.service.board.IBoardService; // IBoardService import
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // ResponseEntity import
import org.springframework.security.core.Authentication; // Authentication import
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping; // PostMapping import
import org.springframework.web.bind.annotation.RequestBody; // RequestBody import
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class BoardController {

    @Autowired
    private BoardDAO boardDAO;
    
    @Autowired
    private IBoardService boardService;

    @GetMapping("/api/posts")
    public List<Map<String, Object>> getAllPosts() {
        return boardDAO.findAll();
    }

    /**
     * 새로운 게시글을 생성합니다.
     * @param post 프론트엔드에서 보낸 게시글 데이터 (title, content)
     * @param authentication 현재 로그인한 사용자 정보
     * @return 생성된 게시글 정보
     */
    @PostMapping("/api/posts")
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Map<String, Object> post, Authentication authentication) {
        // 1. 현재 로그인한 사용자의 ID를 가져옵니다.
        String userId = authentication.getName();

        // 2. boardService에 게시글 데이터와 사용자 ID를 전달하여 생성 로직을 수행합니다.
        Map<String, Object> createdPost = boardService.createPost(post, userId);

        // 3. 성공적으로 생성되면 200 OK 응답을, 실패하면 500 서버 에러 응답을 보냅니다.
        if (createdPost != null) {
            return ResponseEntity.ok(createdPost);
        } else {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ▼▼▼▼▼ [추가] 특정 게시글 조회를 처리하는 API 메소드 ▼▼▼▼▼
    /**
     * 특정 ID의 게시글 하나를 조회합니다.
     * @param postId URL 경로에서 추출한 게시글 ID
     * @return 게시글 정보 또는 404 Not Found 응답
     */
    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<Map<String, Object>> getPostById(@PathVariable int postId) {
        Map<String, Object> post = boardService.getPost(postId);
        if (post != null) {
            return ResponseEntity.ok(post); // 게시글을 찾으면 200 OK 응답
        } else {
            return ResponseEntity.notFound().build(); // 없으면 404 Not Found 응답
        }
    }
}