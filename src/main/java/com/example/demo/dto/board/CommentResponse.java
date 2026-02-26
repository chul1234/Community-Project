package com.example.demo.dto.board;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class CommentResponse {
    private int comment_id;
    private int post_id;
    private String user_id;
    private String content;
    private Integer parent_comment_id;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime created_at;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updated_at;
    
    private String author_name; // From JOIN
    
    // For nested replies (Frontend explicitly expects 'replies' array in boardController.js)
    private List<CommentResponse> replies;
    
    // Default constructor
    public CommentResponse() {}

    // Getters and Setters matching exactly what AngularJS expects (snake_case generally or exactly as the Map was)
    public int getComment_id() { return comment_id; }
    public void setComment_id(int comment_id) { this.comment_id = comment_id; }

    public int getPost_id() { return post_id; }
    public void setPost_id(int post_id) { this.post_id = post_id; }

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getParent_comment_id() { return parent_comment_id; }
    public void setParent_comment_id(Integer parent_comment_id) { this.parent_comment_id = parent_comment_id; }

    public LocalDateTime getCreated_at() { return created_at; }
    public void setCreated_at(LocalDateTime created_at) { this.created_at = created_at; }

    public LocalDateTime getUpdated_at() { return updated_at; }
    public void setUpdated_at(LocalDateTime updated_at) { this.updated_at = updated_at; }

    public String getAuthor_name() { return author_name; }
    public void setAuthor_name(String author_name) { this.author_name = author_name; }

    public List<CommentResponse> getReplies() { return replies; }
    public void setReplies(List<CommentResponse> replies) { this.replies = replies; }
}
