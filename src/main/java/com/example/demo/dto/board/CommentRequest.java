package com.example.demo.dto.board;

public class CommentRequest {
    private String content;
    private Integer parent_comment_id;

    // Default constructor for Jackson JSON deserialization
    public CommentRequest() {}

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getParent_comment_id() {
        return parent_comment_id;
    }

    public void setParent_comment_id(Integer parent_comment_id) {
        this.parent_comment_id = parent_comment_id;
    }
}
