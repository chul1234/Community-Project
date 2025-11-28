// 수정됨: 대용량 게시판 목록 API에 검색 파라미터(searchType, searchKeyword) 추가

package com.example.demo.controller;

// 필요한 클래스 import
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;  // ★ POST/PUT/DELETE 위해 추가
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.bigpost.IBigPostService;

@RestController
public class BigPostController {

    @Autowired
    private IBigPostService bigPostService;

    // ----------------------------------------------------
    // ① 기존 OFFSET 방식 (기능 유지) + 검색 지원
    // ----------------------------------------------------
    @GetMapping("/api/big-posts")
    public Map<String, Object> getBigPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String searchKeyword
    ) {
        return bigPostService.getBigPosts(page, size, searchType, searchKeyword);
    }

    // ----------------------------------------------------
    // ② 초고속 키셋 페이징: 첫 페이지 (OFFSET 없음)
    // ----------------------------------------------------
    @GetMapping("/api/big-posts/first")
    public List<Map<String, Object>> getFirstPage(
            @RequestParam(defaultValue = "20") int size
    ) {
        return bigPostService.getFirstPage(size);
    }

    // ----------------------------------------------------
    // ③ 초고속 키셋 페이징: 다음 페이지
    // ----------------------------------------------------
    @GetMapping("/api/big-posts/next")
    public List<Map<String, Object>> getNextPage(
            @RequestParam long lastId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return bigPostService.getNextPage(lastId, size);
    }


    // ====================================================
    // ▼▼▼ 일반 게시판 스타일 CRUD 추가 ▼▼▼
    // ====================================================

    /**
     * ④ 대용량 게시글 단건 조회
     * 예: GET /api/big-posts/123
     */
    @GetMapping("/api/big-posts/{postId}")
    public Map<String, Object> getPostById(@PathVariable long postId) {
        return bigPostService.getPost(postId);
    }

    /**
     * ⑤ 대용량 게시글 생성
     * 예: POST /api/big-posts
     * Body: { "title": "...", "content": "...", "user_id": "admin" }
     */
    @PostMapping("/api/big-posts")
    public Map<String, Object> createPost(
            @RequestBody Map<String, Object> post,
            Authentication authentication
    ) {

        // 로그인 사용자 ID를 user_id에 주입 (일반 게시판과 동일 패턴)
        if (authentication != null) {
            String userId = authentication.getName();
            if (userId != null && !userId.isEmpty() && post.get("user_id") == null) {
                post.put("user_id", userId);
            }
        }

        int affected = bigPostService.createPost(post);

        if (affected > 0) {
            // DAO에서 PK(post_id)를 post Map에 넣어줌 → 여기서 바로 조회 가능
            long postId = ((Number) post.get("post_id")).longValue();
            return bigPostService.getPost(postId);
        }
        return null;
    }

    /**
     * ⑥ 대용량 게시글 수정
     * 예: PUT /api/big-posts/123
     */
    @PutMapping("/api/big-posts/{postId}")
    public Map<String, Object> updatePost(
            @PathVariable long postId,
            @RequestBody Map<String, Object> postDetails
    ) {
        postDetails.put("post_id", postId); // 안전하게 post_id 주입
        int affected = bigPostService.updatePost(postDetails);

        if (affected > 0) {
            return bigPostService.getPost(postId);
        }
        return null;
    }

    /**
     * ⑦ 대용량 게시글 삭제
     * 예: DELETE /api/big-posts/123
     */
    @DeleteMapping("/api/big-posts/{postId}")
    public boolean deletePost(@PathVariable long postId) {
        return bigPostService.deletePost(postId) > 0;
    }
}

// 수정됨 끝
