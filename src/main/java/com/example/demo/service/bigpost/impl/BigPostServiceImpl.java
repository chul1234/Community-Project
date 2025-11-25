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
    // 기존 OFFSET 기반 페이징 방식
    // --------------------------------------------
    @Override
    public Map<String, Object> getBigPosts(int page, int size) { // OFFSET 방식 조회 메서드
        int offset = (page - 1) * size; // OFFSET 계산: (현재 페이지 - 1) * size

        List<Map<String, Object>> posts = bigPostDAO.findAll(size, offset); // OFFSET + LIMIT 방식으로 데이터 조회
        int totalItems = bigPostDAO.countAll(); // 전체 게시글 수 SELECT COUNT(*)

        int totalPages = (int) Math.ceil((double) totalItems / size); // 페이지 총 개수 계산 (올림)

        Map<String, Object> result = new HashMap<>(); // 결과 Map 생성
        result.put("posts", posts);          // 현재 페이지 게시글 목록
        result.put("totalItems", totalItems); // 전체 게시글 수
        result.put("totalPages", totalPages); // 전체 페이지 개수
        result.put("currentPage", page);      // 현재 페이지 번호

        return result; // 결과 반환
    }

    // ---------------------------------------------------------
    // ▼▼▼ 초고속 키셋 페이징 방식 (OFFSET 사용하지 않음) ▼▼▼
    // ---------------------------------------------------------

    @Override
    public List<Map<String, Object>> getFirstPage(int size) { // 첫 페이지 조회 (키셋 페이징 시작 시 사용)
        return bigPostDAO.findFirstPage(size); // 가장 최신 게시글부터 size 만큼 조회
    }

    @Override
    public List<Map<String, Object>> getNextPage(long lastId, int size) { // 다음 페이지 조회
        return bigPostDAO.findNextPage(lastId, size); // lastId보다 작은 데이터를 size 만큼 조회
    }
}
