package com.example.demo.service.like.impl;

import org.springframework.beans.factory.annotation.Autowired;                  //
import org.springframework.stereotype.Service;   // 
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.LikeDAO;
import com.example.demo.service.like.ILikeService;

/**
 * LikeServiceImpl:
 * 좋아요 기능의 실제 비즈니스 로직을 처리하는 클래스
 * - 좋아요 토글
 * - 좋아요 개수 조회
 */
@Service
public class LikeServiceImpl implements ILikeService {   // : 인터페이스 구현

    @Autowired
    private LikeDAO likeDAO;  // : DAO 주입

    /**
     *좋아요 토글 기능 구현
     * alreadyLiked = true  → DELETE 실행 → false 반환
     * alreadyLiked = false → INSERT 실행 → true 반환
     */
    @Override
    @Transactional   // : INSERT/DELETE 동시 처리 보장
    public boolean toggleLike(String targetType, int targetId, String userId) {

        // 현재 좋아요 상태 확인
        boolean alreadyLiked = likeDAO.exists(targetType, targetId, userId);  // 

        if (alreadyLiked) {
            // 이미 좋아요 누른 상태 → 좋아요 취소
            likeDAO.delete(targetType, targetId, userId);   // 
            return false;   // 현재 상태 OFF
        } else {
            // 아직 안 눌렀으면 좋아요 추가
            likeDAO.insert(targetType, targetId, userId);   // 
            return true;    // 현재 상태 ON
        }
    }

    /**
     *좋아요 개수 조회
     */
    @Override
    public int getLikeCount(String targetType, int targetId) {
        return likeDAO.count(targetType, targetId);   // 
    }

    @Override
    public boolean checkLike(String targetType, int targetId, String userId) {
        return likeDAO.exists(targetType, targetId, userId);
    }
}
