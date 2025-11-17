package com.example.demo.service.like;

/**
 * ILikeService: 좋아요 기능의 서비스 인터페이스
 * 게시글/댓글 대상 좋아요 토글 및 좋아요 개수 조회 기능 선언
 * (구현은 LikeServiceImpl에서 수행)
 */
public interface ILikeService {

    /**
     * ★ 좋아요 토글 기능 (추가됨)
     * 이미 좋아요 상태이면 삭제하고, 아니면 추가한다.
     *
     * @param targetType  'POST' 또는 'COMMENT'
     * @param targetId    대상 게시글/댓글의 ID
     * @param userId      현재 사용자 ID
     * @return true: 좋아요 ON 상태, false: 좋아요 OFF 상태
     */
    boolean toggleLike(String targetType, int targetId, String userId); // 

    /**
     * ★ 좋아요 개수 조회 기능 (추가됨)
     * 대상 게시글/댓글의 총 좋아요 숫자를 가져온다.
     *
     * @param targetType 'POST' 또는 'COMMENT'
     * @param targetId   대상 ID
     * @return 좋아요 개수(int)
     */
    int getLikeCount(String targetType, int targetId); // 
}
