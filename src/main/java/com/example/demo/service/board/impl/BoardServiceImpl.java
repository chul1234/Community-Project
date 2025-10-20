package com.example.demo.service.board.impl;

import com.example.demo.dao.BoardDAO;
import com.example.demo.service.board.IBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;

@Service
public class BoardServiceImpl implements IBoardService {

    @Autowired
    private BoardDAO boardDAO;

    @Override
    public List<Map<String, Object>> getAllPosts() {
        return boardDAO.findAll();
    }

    @Override
    public Map<String, Object> createPost(Map<String, Object> post, String userId) {
        // 1. 게시글 데이터에 현재 로그인한 사용자의 ID를 'user_id'로 추가합니다.
        post.put("user_id", userId);

        // 2. DAO를 통해 데이터베이스에 저장합니다.
        int affectedRows = boardDAO.save(post);

        // 3. 저장이 성공하면(1), 원본 데이터를 반환하고, 실패하면 null을 반환합니다.
        if (affectedRows > 0) {
            return post;
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> getPost(int postId) {
        // DAO를 통해 게시글을 찾고, 없으면 null을 반환합니다.
        return boardDAO.findById(postId).orElse(null);
    }

    @Override
    public Map<String, Object> updatePost(int postId, Map<String, Object> postDetails, String currentUserId) {
        // 1. 수정할 게시글을 DB에서 가져옵니다.
        Map<String, Object> post = boardDAO.findById(postId).orElse(null);

        // 2. 게시글이 존재하고, 현재 로그인한 사용자가 작성자인지 확인합니다.
        if (post != null && post.get("user_id").equals(currentUserId)) {
            // 3. 작성자가 맞으면, 수정할 내용을 post 객체에 반영합니다.
            post.put("title", postDetails.get("title"));
            post.put("content", postDetails.get("content"));
            
            // 4. DAO를 통해 DB에 업데이트하고, 성공하면 수정된 post를 반환합니다.
            boardDAO.update(post);
            return post;
        }
        // 게시글이 없거나 작성자가 아니면 null을 반환합니다.
        return null;
    }

    @Override
    public boolean deletePost(int postId, String currentUserId, List<String> roles) {
        Map<String, Object> post = boardDAO.findById(postId).orElse(null);

        // [핵심] 게시글이 존재하고, (사용자가 ADMIN이거나 || 사용자가 작성자 본인이라면)
        if (post != null && (roles.contains("ADMIN") || post.get("user_id").equals(currentUserId))) {
            return boardDAO.delete(postId) > 0;
        }
        return false;
    }
}