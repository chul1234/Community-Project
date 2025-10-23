package com.example.demo.service.board.impl;

import com.example.demo.dao.BoardDAO; // DB의 posts 테이블과 통신하는 BoardDAO
import com.example.demo.service.board.IBoardService; // 게시판 서비스의 설계도(인터페이스)
import org.springframework.beans.factory.annotation.Autowired; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import org.springframework.stereotype.Service; // 이 클래스가 서비스 부품임을 알리는 도구

import java.util.HashMap; // HashMap 사용 (Map 구현체)
import java.util.Map; // 데이터를 '이름표-값' 쌍으로 다루기 위한 도구
import java.util.List; // 여러 데이터를 목록 형태로 다루기 위한 도구

@Service
public class BoardServiceImpl implements IBoardService {

    @Autowired
    private BoardDAO boardDAO;

    /**
     * [수정됨] 특정 페이지의 게시글 목록과 전체 페이지 정보를 조회 메소드
     * @param page 요청 페이지 번호 (int, 1부터 시작)
     * @param size 페이지당 게시글 수 (int)
     * @return Map (키: "posts" -> 게시글 목록, "totalPages" -> 전체 페이지 수, "totalItems" -> 전체 게시글 수, "currentPage" -> 현재 페이지 번호)
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public Map<String, Object> getAllPosts(int page, int size) { // 메소드 시그니처 수정됨 (파라미터 추가, 반환 타입 변경)
        // 1. offset 계산 (DB에서 건너뛸 게시글 수). 페이지는 1부터 시작하므로 page - 1
        int offset = (page - 1) * size;

        // 2. DAO를 통해 해당 페이지 게시글 목록 조회. boardDAO.findAll() 호출 시 limit(size), offset 전달
        List<Map<String, Object>> posts = boardDAO.findAll(size, offset);

        // 3. DAO를 통해 전체 게시글 수 조회. boardDAO.countAll() 호출
        int totalItems = boardDAO.countAll();

        // 4. 전체 페이지 수 계산. Math.ceil() 사용하여 올림 처리 (예: 21개 글 / 10개씩 = 2.1 -> 3페이지)
        int totalPages = (int) Math.ceil((double) totalItems / size);

        // 5. 결과를 담을 HashMap 생성
        Map<String, Object> result = new HashMap<>();
        // .put() 메소드로 결과 데이터 저장
        result.put("posts", posts);         // 현재 페이지 게시글 목록
        result.put("totalItems", totalItems); // 전체 게시글 수
        result.put("totalPages", totalPages);   // 전체 페이지 수
        result.put("currentPage", page);     // 요청된 현재 페이지 번호

        // 6. 완성된 Map 반환
        return result;
    }

    @Override
    public Map<String, Object> createPost(Map<String, Object> post, String userId) {
        // 1. 게시글 데이터에 현재 로그인한 사용자의 ID를 'user_id'로 추가합니다.
        // 현재 로그인한 사용자 ID추가
        post.put("user_id", userId); // post 맵에 user_id 추가

        // 2. 작성자 정보가 추가된 완전한 게시글 데이터를 boardDAO의 save 메소드로 전달하여 DB에 저장을 요청
        //affectedRows 변수에는 DB에 저장된 행의 수(성공 시 1)가 저장
        int affectedRows = boardDAO.save(post); // boardDAO.save() 호출

        // 3. 만약 저장된 행의 수가 0보다 크다면 (저장이 성공했다면)
        if (affectedRows > 0) {
            // 원본 게시글 데이터를 컨트롤러로 반환
            return post;
        } else {
            // null을 반환하여 컨트롤러에게 실패했음을 알립니다.
            return null;
        }
    }

    @Override
    public Map<String, Object> getPost(int postId) {
        // boardDAO에게 postId를 전달하여 특정 게시글을 찾아달라고 요청
        // boardDAO의 findById는 Optional<Map>을 반환하므로, .orElse(null)을 사용
        // DAO를 통해 게시글을 찾고, 없으면 null을 반환합니다.
        return boardDAO.findById(postId).orElse(null); // boardDAO.findById() 호출, 결과 없으면 null 반환
    }

    @Override
    public Map<String, Object> updatePost(int postId, Map<String, Object> postDetails, String currentUserId) {
        // 1. 수정할 게시글이 실제로 DB에 존재하는지 확인하기 위해 postId로 게시글을 조회.
        Map<String, Object> post = boardDAO.findById(postId).orElse(null); // boardDAO.findById() 호출

        // 2. [권한 확인 로직]
        // 만약 게시글이 존재하고(post != null) '그리고'(&&) 게시글의 작성자('user_id')
        //  현재 로그인한 사용자(currentUserId)와 같다면, 아래 코드를 실행
        if (post != null && post.get("user_id").equals(currentUserId)) {
            // 3. 작성자가 맞으면, 컨트롤러로부터 받은 새로운 제목(title)과 내용(content)
            // 기존 post Map의 데이터 불러옴
            post.put("title", postDetails.get("title")); // 제목 업데이트
            post.put("content", postDetails.get("content")); // 내용 업데이트

            // 4.수정된 내용이 담긴 post Map을 boardDAO의 update 메소드로 전달,DB에 업데이트를 요청
            int affectedRows = boardDAO.update(post); // boardDAO.update() 호출
            return affectedRows > 0 ? post : null; // 성공 시 post 반환, 실패 시 null 반환
        }
        // 게시글이 없거나 작성자가 아니면 null을 반환합니다.
        return null;
    }

    @Override
    public boolean deletePost(int postId, String currentUserId, List<String> roles) {
        //제할 게시글이 실제로 DB에 존재하는지 확인하기 위해 postId로 게시글을 조회
        Map<String, Object> post = boardDAO.findById(postId).orElse(null); // boardDAO.findById() 호출

        // 만약 게시글이 존재하고(post != null) '그리고'(&&)
        //현재 사용자의 역할 목록(roles)에 'ADMIN'이 포함되어 있거나(||) 또는 게시글의 작성자('user_id')가 현재 로그인한 사용자(currentUserId)와 같다면 아래 코드를 실행
        if (post != null && (roles.contains("ADMIN") || post.get("user_id").equals(currentUserId))) {
            //권한이 있다면, boardDAO의 delete 메소드를 호출하여 DB에서 삭제를 요청
            //그 결과(영향받은 행의 수)가 0보다 큰지(삭제 성공 여부, true/false)를 반환
            return boardDAO.delete(postId) > 0; // boardDAO.delete() 호출
        }
        return false; // 권한 없으면 false 반환
    }

    /**
     * [유지] 특정 게시글의 조회수를 증가시킵니다.
     * @param postId 조회수를 증가시킬 게시글의 ID
     */
    @Override // IBoardService 인터페이스 메소드 구현 명시
    public void incrementViewCount(int postId) {
        // boardDAO의 incrementViewCount 메소드 호출
        boardDAO.incrementViewCount(postId);
    }
}