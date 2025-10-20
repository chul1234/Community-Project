package com.example.demo.service.comment;

import java.util.List;
import java.util.Map;

public interface ICommentService {
    List<Map<String, Object>> getCommentsByPostId(int postId);
    Map<String, Object> createComment(int postId, Map<String, Object> comment, String currentUserId);
    Map<String, Object> updateComment(int commentId, Map<String, Object> commentDetails, String currentUserId);
    boolean deleteComment(int commentId, String currentUserId, List<String> roles);
}