// 수정됨: 대용량 게시판 검색 기능 추가 (getBigPosts에서 검색 처리 분기)

package com.example.demo.service.bigpost.impl; // 서비스 구현 클래스가 속한 패키지 선언

// 필요한 클래스 import
import java.util.HashMap; // Map 구현체(HashMap)를 사용하기 위해 import
import java.util.List;    // 리스트 타입 사용
import java.util.Map;     // Map 인터페이스 사용

import org.springframework.beans.factory.annotation.Autowired; // @Autowired 의존성 주입
import org.springframework.stereotype.Service;                  // @Service 어노테이션

import com.example.demo.dao.BigPostDAO;      // 대용량 게시글 DAO
import com.example.demo.service.bigpost.IBigPostService; // 서비스 인터페이스

@Service // 스프링 서비스 계층 컴포넌트로 등록
public class BigPostServiceImpl implements IBigPostService {

    @Autowired // BigPostDAO를 스프링이 자동 주입
    private BigPostDAO bigPostDAO;

    // --------------------------------------------
    // 기존 OFFSET 기반 페이징 방식 + 검색
    // --------------------------------------------
    @Override
    public Map<String, Object> getBigPosts(int page, int size, String searchType, String searchKeyword) { // OFFSET 방식 조회 메서드
        // 1. OFFSET 계산: (현재 페이지 - 1) * size
        int offset = (page - 1) * size;

        // 2. OFFSET + LIMIT 방식으로 데이터 조회 (검색 조건 함께 전달)
        List<Map<String, Object>> posts = bigPostDAO.findAll(size, offset, searchType, searchKeyword);

        // 3. 전체 게시글 수 조회
        //    - 검색어가 없으면: 카운터 테이블(total_count) 사용 (매우 빠름)
        //    - 검색어가 있으면: 조건이 걸린 COUNT(*) 실행
        int totalItems;
        if (searchKeyword == null || searchKeyword.isEmpty()) {
            totalItems = bigPostDAO.countAll(null, null);   // 카운터 테이블 사용
        } else {
            totalItems = bigPostDAO.countAll(searchType, searchKeyword); // 조건 COUNT
        }

        // 4. 페이지 총 개수 계산 (올림)
        int totalPages = (size > 0)
                ? (int) Math.ceil((double) totalItems / size)
                : 0;

        // 5. 결과 Map 구성
        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);           // 현재 페이지 게시글 목록
        result.put("totalItems", totalItems); // 전체(또는 검색 결과) 게시글 수
        result.put("totalPages", totalPages); // 전체 페이지 개수
        result.put("currentPage", page);      // 현재 페이지 번호

        // 6. 결과 반환
        return result;
    }

    // ---------------------------------------------------------
    // ▼▼▼ 초고속 키셋 페이징 방식 (OFFSET 사용하지 않음) ▼▼▼
    // ---------------------------------------------------------

    @Override
    public List<Map<String, Object>> getFirstPage(int size) { // 첫 페이지 조회 (키셋 페이징 시작 시 사용)
        // 가장 최신 게시글부터 size 만큼 조회
        return bigPostDAO.findFirstPage(size);
    }

    @Override
    public List<Map<String, Object>> getNextPage(long lastId, int size) { // 다음 페이지 조회
        // lastId보다 post_id가 작은 데이터 중에서 size 만큼 조회
        return bigPostDAO.findNextPage(lastId, size);
    }

    // ------------------------------------------------------
    // ▼▼▼ 일반 게시판 스타일 CRUD 메서드 ▼▼▼
    // ------------------------------------------------------

    @Override
    public Map<String, Object> getPost(long postId) {
        // DAO에서 Optional<Map> 형태로 받아와서, 없으면 null 반환
        return bigPostDAO.findById(postId).orElse(null);
    }

    @Override
    public int createPost(Map<String, Object> post) {
        // 단순히 DAO의 insert 호출
        // BigPostDAO.insert() 내부에서:
        //  - big_posts INSERT
        //  - big_posts_counter.total_count +1
        return bigPostDAO.insert(post);
    }

    @Override
    public int updatePost(Map<String, Object> post) {
        // post Map 안에 post_id, title, content 등이 들어있다고 가정
        // 카운터(total_count)는 변경되지 않음
        return bigPostDAO.update(post);
    }

    @Override
    public int deletePost(long postId) {
        // BigPostDAO.delete() 내부에서:
        //  - big_posts DELETE
        //  - big_posts_counter.total_count -1
        return bigPostDAO.delete(postId);
    }
}

// 수정됨 끝
