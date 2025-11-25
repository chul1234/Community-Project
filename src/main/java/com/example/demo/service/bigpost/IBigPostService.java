// 수정됨: 대용량 게시판 CRUD 메서드 추가

package com.example.demo.service.bigpost;

import java.util.List;
import java.util.Map;

public interface IBigPostService {

    // 기존 OFFSET 페이징 (page / size 기반 목록 조회)
    Map<String, Object> getBigPosts(int page, int size);

    // ▼▼▼ 초고속 키셋 페이징용 ▼▼▼
    // 첫 페이지(가장 최신 글 기준) 조회
    List<Map<String, Object>> getFirstPage(int size);

    // 다음 페이지 조회 (현재 페이지 마지막 post_id 기준으로 이어서 조회)
    List<Map<String, Object>> getNextPage(long lastId, int size);

    // ------------------------------------------------------
    // ▼▼▼ 일반 게시판 스타일 CRUD 메서드 ▼▼▼
    // ------------------------------------------------------

    /**
     * 대용량 게시글 단건 조회
     * @param postId 조회할 게시글 ID
     * @return 게시글 정보(Map) 없으면 null
     */
    Map<String, Object> getPost(long postId);

    /**
     * 대용량 게시글 생성
     * @param post 제목, 내용, 작성자 등 정보가 담긴 Map
     * @return INSERT로 영향받은 행 수 (성공 시 1, 실패 시 0)
     */
    int createPost(Map<String, Object> post);

    /**
     * 대용량 게시글 수정
     * @param post 수정할 데이터(Map) - 반드시 post_id 포함
     * @return UPDATE로 영향받은 행 수 (성공 시 1, 실패 시 0)
     */
    int updatePost(Map<String, Object> post);

    /**
     * 대용량 게시글 삭제
     * @param postId 삭제할 게시글 ID
     * @return DELETE로 영향받은 행 수 (성공 시 1, 실패 시 0)
     */
    int deletePost(long postId);
}

// 수정됨 끝
