package com.example.demo.service.comment.impl;

import com.example.demo.dao.CommentDAO;
import com.example.demo.service.comment.ICommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class CommentServiceImpl implements ICommentService {

    @Autowired
    private CommentDAO commentDAO;

    @Override
    public List<Map<String, Object>> getCommentsByPostId(int postId) {
        return commentDAO.findByPostId(postId);
    }

    @Override
    public Map<String, Object> createComment(int postId, Map<String, Object> comment, String currentUserId) {
        comment.put("post_id", postId);
        comment.put("user_id", currentUserId);
        int affectedRows = commentDAO.save(comment);
        return affectedRows > 0 ? comment : null;
    }

    @Override
    public Map<String, Object> updateComment(int commentId, Map<String, Object> commentDetails, String currentUserId) {
        Map<String, Object> comment = commentDAO.findById(commentId).orElse(null);
        // [권한 확인] 댓글이 존재하고, 작성자 본인일 경우에만 수정
        if (comment != null && comment.get("user_id").equals(currentUserId)) {
            comment.put("content", commentDetails.get("content"));
            commentDAO.update(comment);
            return comment;
        }
        return null;
    }

    @Override
    public boolean deleteComment(int commentId, String currentUserId, List<String> roles) {
        Map<String, Object> comment = commentDAO.findById(commentId).orElse(null);
        // [권한 확인] 댓글이 존재하고, (관리자이거나 || 작성자 본인일 경우) 삭제
        if (comment != null && (roles.contains("ADMIN") || comment.get("user_id").equals(currentUserId))) {
            return commentDAO.delete(commentId) > 0;
        }
        return false;
    }
}