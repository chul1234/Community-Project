package com.example.demo.controller;

import java.util.HashMap;   // ★ 추가됨
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.like.ILikeService;

/**
 * LikeController:
 * 좋아요 기능의 REST API 엔드포인트를 제공하는 컨트롤러
 * - /likes/toggle : 좋아요 On/Off 기능
 * - /likes/count  : 좋아요 개수 조회
 */
@RestController   // ★ 추가됨
public class LikeController {

    @Autowired
    private ILikeService likeService;    // ★ 추가됨

    /**
     * ★ 좋아요 토글 API
     * 프론트에서 type(POST/COMMENT), id(targetId), userId 를 전달받아
     * 좋아요 상태를 반전시킨다.
     *
     * 결과 JSON:
     * {
     *   "liked": true/false,
     *   "count": 숫자
     * }
     */
    @PostMapping("/likes/toggle")   // ★ 추가됨
    public Map<String, Object> toggleLike(
            @RequestParam("type") String targetType,   // POST 또는 COMMENT
            @RequestParam("id") int targetId,          // post_id 또는 comment_id
            @RequestParam String userId      // 현재 로그인 사용자
    ) {
        // 좋아요 토글 실행
        boolean liked = likeService.toggleLike(targetType, targetId, userId);  // ★ 추가됨

        // 변경 후 좋아요 개수 조회
        int count = likeService.getLikeCount(targetType, targetId);            // ★ 추가됨

        // 응답 JSON 생성
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);   // 현재 좋아요 상태
        result.put("count", count);   // 총 좋아요 수

        return result;  // JSON 반환
    }


    /**
     * ★ 좋아요 개수 단독 조회 API (선택)
     * 게시글/댓글을 처음 로딩할 때 count만 필요할 경우 사용 가능
     *
     * 요청:
     * /likes/count?type=POST&id=3
     */
    @GetMapping("/likes/count")     // ★ 추가됨
    public Map<String, Object> getLikeCount(
            @RequestParam("type") String targetType,
            @RequestParam("id") int targetId
    ) {
        int count = likeService.getLikeCount(targetType, targetId);  // ★ 추가됨

        Map<String, Object> result = new HashMap<>();
        result.put("count", count);

        return result;
    }
}
