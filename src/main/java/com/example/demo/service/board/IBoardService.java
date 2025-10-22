package com.example.demo.service.board;

import java.util.List; // List를 사용하기 위해 import를 추가해야 합니다.
import java.util.Map;

public interface IBoardService {

    // 모든 게시글을 조회하는 기능 선언 
    List<Map<String, Object>> getAllPosts();

    // 게시글 생성
    Map<String, Object> createPost(Map<String, Object> post, String userId);

    // 특정 게시글 조회
    Map<String, Object> getPost(int postId);

    // [추가] 게시글 수정
    Map<String, Object> updatePost(int postId, Map<String, Object> postDetails, String currentUserId);

    // [수정] 게시글 삭제 (파라미터에 List<String> roles 추가)
    boolean deletePost(int postId, String currentUserId, List<String> roles);
    
    // 특정 게시글의 조회수 증가
    void incrementViewCount(int postId);
}