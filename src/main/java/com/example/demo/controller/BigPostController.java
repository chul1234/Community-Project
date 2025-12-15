// 수정됨: GET 상세 조회에서 조회수(view_count) +1 반영(수정/삭제 응답에서는 증가 안 함)

package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.bigpost.IBigPostService;

@RestController
@RequestMapping("/api/big-posts")
public class BigPostController {

    @Autowired
    private IBigPostService bigPostService;

    // ------------------------------------------
    // (검색 포함) OFFSET 기반 목록 조회
    // ------------------------------------------
    @GetMapping
    public Map<String, Object> getBigPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "1000") int size,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String searchKeyword
    ) {
        return bigPostService.getBigPosts(page, size, searchType, searchKeyword);
    }

    // ------------------------------------------
    // 키셋 페이징: 첫 페이지
    // ------------------------------------------
    @GetMapping("/first")
    public List<Map<String, Object>> getFirstPage(@RequestParam(defaultValue = "1000") int size) {
        return bigPostService.getFirstPage(size);
    }

    // ------------------------------------------
    // 키셋 페이징: 다음 페이지
    // ------------------------------------------
    @GetMapping("/next")
    public List<Map<String, Object>> getNextPage(
            @RequestParam long lastId,
            @RequestParam(defaultValue = "1000") int size
    ) {
        return bigPostService.getNextPage(lastId, size);
    }

    // ------------------------------------------
    // 단건 조회(상세) - 여기서만 조회수 +1
    // ------------------------------------------
    @GetMapping("/{postId}")
    public Map<String, Object> getPostById(@PathVariable long postId) {
        return bigPostService.getPostAndIncreaseViewCount(postId);
    }

    // ------------------------------------------
    // 등록
    // ------------------------------------------
    @PostMapping
    public Map<String, Object> createPost(@RequestBody Map<String, Object> post) {
        int affected = bigPostService.createPost(post);

        Map<String, Object> result = new HashMap<>();
        result.put("affected", affected);
        result.put("post_id", post.get("post_id"));
        return result;
    }

    // ------------------------------------------
    // 수정
    // ------------------------------------------
    @PutMapping("/{postId}")
    public Map<String, Object> updatePost(@PathVariable long postId, @RequestBody Map<String, Object> post) {
        post.put("post_id", postId);
        int affected = bigPostService.updatePost(post);

        Map<String, Object> result = new HashMap<>();
        result.put("affected", affected);
        result.put("post", bigPostService.getPost(postId)); // 수정 응답에서는 조회수 증가 금지
        return result;
    }

    // ------------------------------------------
    // 삭제
    // ------------------------------------------
    @DeleteMapping("/{postId}")
    public Map<String, Object> deletePost(@PathVariable long postId) {
        int affected = bigPostService.deletePost(postId);

        Map<String, Object> result = new HashMap<>();
        result.put("affected", affected);
        return result;
    }
}

// 수정됨 끝
