package com.example.demo.service.comment;

import com.example.demo.dto.board.CommentRequest;
import com.example.demo.dto.board.CommentResponse;
import java.util.List;

public interface ICommentService {
    List<CommentResponse> getCommentsByPostId(int postId);
    
    CommentResponse createComment(int postId, CommentRequest commentRequest, String currentUserId);
    
    CommentResponse updateComment(int commentId, CommentRequest commentDetails, String currentUserId);
    
    boolean deleteComment(int commentId, String currentUserId, List<String> roles);
}