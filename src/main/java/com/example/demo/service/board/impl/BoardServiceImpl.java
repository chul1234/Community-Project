package com.example.demo.service.board.impl;

import com.example.demo.dao.BoardDAO;
import com.example.demo.service.board.IBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class BoardServiceImpl implements IBoardService {

    @Autowired
    private BoardDAO boardDAO;

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
}