package com.example.demo.service.board.impl;

import com.example.demo.dao.BoardDAO; // DB의 posts 테이블과 통신하는 BoardDAO
import com.example.demo.service.board.IBoardService; // 게시판 서비스의 설계도(인터페이스)
import org.springframework.beans.factory.annotation.Autowired; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import org.springframework.stereotype.Service; // 이 클래스가 서비스 부품임을 알리는 도구

import java.util.Map; // 데이터를 '이름표-값' 쌍으로 다루기 위한 도구
import java.util.List; // 여러 데이터를 목록 형태로 다루기 위한 도구

@Service
public class BoardServiceImpl implements IBoardService {

    @Autowired
    private BoardDAO boardDAO;

    @Override
    public List<Map<String, Object>> getAllPosts() {
        // boardDAO에게 모든 게시글을 찾아달라고 요청,받은 결과를 그대로 컨트롤러로 반환
        return boardDAO.findAll();
    }

    @Override
    public Map<String, Object> createPost(Map<String, Object> post, String userId) {
        // 1. 게시글 데이터에 현재 로그인한 사용자의 ID를 'user_id'로 추가합니다.
        // 현재 로그인한 사용자 ID추가
        post.put("user_id", userId);

        // 2. 작성자 정보가 추가된 완전한 게시글 데이터를 boardDAO의 save 메소드로 전달하여 DB에 저장을 요청
        //affectedRows 변수에는 DB에 저장된 행의 수(성공 시 1)가 저장
        int affectedRows = boardDAO.save(post);

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
        return boardDAO.findById(postId).orElse(null);
    }

    @Override
    public Map<String, Object> updatePost(int postId, Map<String, Object> postDetails, String currentUserId) {
        // 1. 수정할 게시글이 실제로 DB에 존재하는지 확인하기 위해 postId로 게시글을 조회.
        Map<String, Object> post = boardDAO.findById(postId).orElse(null);

        // 2. [권한 확인 로직]
        // 만약 게시글이 존재하고(post != null) '그리고'(&&) 게시글의 작성자('user_id')
        //  현재 로그인한 사용자(currentUserId)와 같다면, 아래 코드를 실행
        if (post != null && post.get("user_id").equals(currentUserId)) {
            // 3. 작성자가 맞으면, 컨트롤러로부터 받은 새로운 제목(title)과 내용(content)
            // 기존 post Map의 데이터 불러옴
            post.put("title", postDetails.get("title"));
            post.put("content", postDetails.get("content"));
            
            // 4.수정된 내용이 담긴 post Map을 boardDAO의 update 메소드로 전달,DB에 업데이트를 요청
            return post;
        }
        // 게시글이 없거나 작성자가 아니면 null을 반환합니다.
        return null;
    }

    @Override
    public boolean deletePost(int postId, String currentUserId, List<String> roles) {
        //제할 게시글이 실제로 DB에 존재하는지 확인하기 위해 postId로 게시글을 조회
        Map<String, Object> post = boardDAO.findById(postId).orElse(null);

        // 만약 게시글이 존재하고(post != null) '그리고'(&&)
        //현재 사용자의 역할 목록(roles)에 'ADMIN'이 포함되어 있거나(||) 또는 게시글의 작성자('user_id')가 현재 로그인한 사용자(currentUserId)와 같다면 아래 코드를 실행
        if (post != null && (roles.contains("ADMIN") || post.get("user_id").equals(currentUserId))) {
            //권한이 있다면, boardDAO의 delete 메소드를 호출하여 DB에서 삭제를 요청
            //그 결과(영향받은 행의 수)가 0보다 큰지(삭제 성공 여부, true/false)를 반환
            return boardDAO.delete(postId) > 0;
        }
        return false;
    }
}