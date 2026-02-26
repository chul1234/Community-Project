package com.example.demo.entity;

import java.time.LocalDateTime;

public class Comment {
    private int commentId;
    private int postId;
    private String userId; // Author
    private String content;
    private Integer parentCommentId; // Can be null for top-level comments
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Properties strictly for display purposes (JOIN data)
    private String authorName; 

    // Getters and Setters
    public int getCommentId() { return commentId; }
    public void setCommentId(int commentId) { this.commentId = commentId; }
    
    public int getPostId() { return postId; }
    public void setPostId(int postId) { this.postId = postId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Integer getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Integer parentCommentId) { this.parentCommentId = parentCommentId; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
}
