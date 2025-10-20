package com.example.demo.service.board;

import java.util.Map;

public interface IBoardService {
    Map<String, Object> createPost(Map<String, Object> post, String userId);
    Map<String, Object> getPost(int postId);
}