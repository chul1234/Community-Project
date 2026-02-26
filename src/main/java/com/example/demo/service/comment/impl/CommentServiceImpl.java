package com.example.demo.service.comment.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.dao.CommentDAO;
import com.example.demo.entity.Comment;
import com.example.demo.dto.board.CommentRequest;
import com.example.demo.dto.board.CommentResponse;
import com.example.demo.service.comment.ICommentService;

@Service
public class CommentServiceImpl implements ICommentService {

    @Autowired
    private CommentDAO commentDAO;

    // Helper to map Entity to DTO
    private CommentResponse convertToDto(Comment comment) {
        CommentResponse dto = new CommentResponse();
        dto.setComment_id(comment.getCommentId());
        dto.setPost_id(comment.getPostId());
        dto.setUser_id(comment.getUserId());
        dto.setContent(comment.getContent());
        dto.setParent_comment_id(comment.getParentCommentId());
        dto.setCreated_at(comment.getCreatedAt());
        // UpdatedAt is omitted in current DAO implementation, but mapped here if present
        dto.setAuthor_name(comment.getAuthorName());
        dto.setReplies(new ArrayList<>()); // Initialize replies list
        return dto;
    }

    @Override 
    public List<CommentResponse> getCommentsByPostId(int postId) {
        List<Comment> flatList = commentDAO.findByPostId(postId);
        
        List<CommentResponse> hierarchicalList = new ArrayList<>();
        Map<Integer, CommentResponse> lookupMap = new HashMap<>();

        // 1. Convert all Entities to DTOs and put in lookup map
        for (Comment comment : flatList) {
            CommentResponse dto = convertToDto(comment);
            lookupMap.put(dto.getComment_id(), dto);
        }

        // 2. Build the tree
        for (Comment comment : flatList) {
            Integer parentId = comment.getParentCommentId();
            CommentResponse currentDto = lookupMap.get(comment.getCommentId());

            if (parentId != null) {
                CommentResponse parentDto = lookupMap.get(parentId);
                if (parentDto != null) {
                    parentDto.getReplies().add(currentDto);
                } else {
                    // Orphan comment, treat as top-level
                    hierarchicalList.add(currentDto);
                }
            } else {
                // Top-level comment
                hierarchicalList.add(currentDto);
            }
        }
        
        return hierarchicalList;
    }

    @Override
    public CommentResponse createComment(int postId, CommentRequest commentRequest, String currentUserId) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setContent(commentRequest.getContent());
        comment.setParentCommentId(commentRequest.getParent_comment_id());
        
        int affectedRows = commentDAO.save(comment);
        
        if (affectedRows > 0) {
            // Note: The DAO doesn't return the generated Key yet. In a complete refactoring,
            // we'd use KeyHolder to get the generated ID. For now, we return a DTO with available info.
            return convertToDto(comment); 
        }
        return null;
    }

    @Override
    public CommentResponse updateComment(int commentId, CommentRequest commentRequest, String currentUserId) {
        Comment comment = commentDAO.findById(commentId).orElse(null);
        
        if (comment != null && comment.getUserId().equals(currentUserId)) {
            comment.setContent(commentRequest.getContent());
            int affectedRows = commentDAO.update(comment);
            if(affectedRows > 0) {
                 return convertToDto(comment);
            }
        }
        return null;
    }

    @Override
    public boolean deleteComment(int commentId, String currentUserId, List<String> roles) {
        Comment comment = commentDAO.findById(commentId).orElse(null);
        if (comment != null && (roles.contains("ADMIN") || comment.getUserId().equals(currentUserId))) {
            return commentDAO.delete(commentId) > 0;
        }
        return false;
    }
}