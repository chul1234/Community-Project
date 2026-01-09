package com.example.demo.controller;

import java.util.HashMap;   // 
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
@RestController   // 
public class LikeController {

    @Autowired
    private ILikeService likeService;    // 

    /**
     *좋아요 토글 API
     * 프론트에서 type(POST/COMMENT), id(targetId), userId 를 전달받아
     * 좋아요 상태를 반전시킨다.
     *
     * 결과 JSON:
     * {
     *   "liked": true/false,
     *   "count": 숫자
     * }
     */
    @PostMapping("/likes/toggle")   // 
    public Map<String, Object> toggleLike(
            @RequestParam("type") String targetType,   // POST 또는 COMMENT
            @RequestParam("id") int targetId,          // post_id 또는 comment_id
            @RequestParam String userId      // 현재 로그인 사용자
    ) {
        // 좋아요 토글 실행
        boolean liked = likeService.toggleLike(targetType, targetId, userId);  // 

        // 변경 후 좋아요 개수 조회
        int count = likeService.getLikeCount(targetType, targetId);            // 

        // 응답 JSON 생성
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);   // 현재 좋아요 상태
        result.put("count", count);   // 총 좋아요 수

        return result;  // JSON 반환
    }


    /**
     * 좋아요 개수 + 여부 조회 API
     * 게시글/댓글을 로딩할 때 count와 함께 "내가 좋아요를 눌렀는지"도 확인
     *
     * 요청:
     * /likes/count?type=POST&id=3&userId=user1
     */
    @GetMapping("/likes/count")
    public Map<String, Object> getLikeCount(
            @RequestParam("type") String targetType,
            @RequestParam("id") int targetId,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        int count = likeService.getLikeCount(targetType, targetId);
        boolean liked = false;

        if (userId != null && !userId.isEmpty()) {
            liked = likeService.checkLike(targetType, targetId, userId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("liked", liked);

        return result;
    }
}
