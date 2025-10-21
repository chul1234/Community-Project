package com.example.demo.service.comment;

import java.util.List;
import java.util.Map;

public interface ICommentService {
    // 특정 게시글의 모든 댓글 조회
    List<Map<String, Object>> getCommentsByPostId(int postId);
    // 댓글 생성
    Map<String, Object> createComment(int postId, Map<String, Object> comment, String currentUserId);
    // 댓글 수정
    Map<String, Object> updateComment(int commentId, Map<String, Object> commentDetails, String currentUserId);
    // 댓글 삭제
    boolean deleteComment(int commentId, String currentUserId, List<String> roles);
}