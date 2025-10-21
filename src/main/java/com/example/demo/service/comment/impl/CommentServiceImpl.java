package com.example.demo.service.comment.impl;

import com.example.demo.dao.CommentDAO; // DB의 comments 테이블과 통신하는 CommentDAO
import com.example.demo.service.comment.ICommentService; // 댓글 서비스의 설계도(인터페이스)
import org.springframework.beans.factory.annotation.Autowired; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import org.springframework.stereotype.Service; // 이 클래스가 서비스 부품임을 알리는 도구
import java.util.List; // 여러 데이터를 목록 형태로 다루기 위한 도구
import java.util.Map; // 데이터를 '이름표-값' 쌍으로 다루기 위한 도구

@Service
//'CommentServiceImpl'이라는 클래스를 선언하고, 'ICommentService' 설계도를 구현(implements)
public class CommentServiceImpl implements ICommentService {

    @Autowired
    private CommentDAO commentDAO;

    @Override 
    public List<Map<String, Object>> getCommentsByPostId(int postId) {
        //commentDAO에게 postId를 전달하여 해당 게시글의 모든 댓글을 찾아달라고 요청하고, 받은 결과를 그대로 컨트롤러로 반환
        return commentDAO.findByPostId(postId);
    }

    //@Override: 부모 설계도(ICommentService)에 정의된 메소드를 재정의
    @Override
    public Map<String, Object> createComment(int postId, Map<String, Object> comment, String currentUserId) {
        //1. 컨트롤러로부터 받은 댓글 데이터(comment Map)에, 'post_id'라는 이름표(key)로 현재 댓글이 달릴 게시글의 ID(postId)를 추가
        comment.put("post_id", postId);
        // 2. 'user_id'라는 이름표(key)로 현재 로그인한 사용자의 ID(currentUserId)를 추가
        comment.put("user_id", currentUserId);
        // 3. 작성자 정보와 게시글 ID가 추가된 완전한 댓글 데이터를 commentDAO의 save 메소드로 전달하여 DB에 저장을 요청
        int affectedRows = commentDAO.save(comment);
        // affectedRows 변수에는 DB에 저장된 행의 수(성공 시 1)가 저장
        return affectedRows > 0 ? comment : null;
        // 4. (삼항 연산자) 만약 저장된 행의 수가 0보다 크다면(저장 성공), 원본 댓글 데이터를 반환하고,그렇지 않다면(저장 실패), null을 반환
    }

    @Override
    public Map<String, Object> updateComment(int commentId, Map<String, Object> commentDetails, String currentUserId) {
        //1. 먼저, 수정할 댓글이 실제로 DB에 존재하는지 확인하기 위해 commentId로 댓글을 조회
        Map<String, Object> comment = commentDAO.findById(commentId).orElse(null);
        // 2. [권한 확인 로직]
        // 만약 댓글이 존재하고(comment != null) '그리고'(&&) 댓글의 작성자('user_id')가 현재 로그인한 사용자(currentUserId)와 같다면, 아래 코드를 실행
        if (comment != null && comment.get("user_id").equals(currentUserId)) {
            // 3. 작성자가 맞으면, 컨트롤러로부터 받은 새로운 내용(content)으로 댓글 내용을 업데이트
            comment.put("content", commentDetails.get("content"));
            // 4. 업데이트된 댓글 데이터를 commentDAO의 update 메소드로 전달하여 DB에 반영을 요청
            commentDAO.update(comment);
            return comment;
        }
        return null;
    }

    @Override
    public boolean deleteComment(int commentId, String currentUserId, List<String> roles) {
       // 먼저, 삭제할 댓글이 실제로 DB에 존재하는지 확인하기 위해 commentId로 댓글을 조회
        Map<String, Object> comment = commentDAO.findById(commentId).orElse(null);
        // 만약 댓글이 존재하고(comment != null) '그리고'(&&) 댓글의 작성자('user_id')가 현재 로그인한 사용자(currentUserId)와 같다면, 아래 코드를 실행
        if (comment != null && (roles.contains("ADMIN") || comment.get("user_id").equals(currentUserId))) {
            // 댓글 작성자이거나 ADMIN 역할이 있으면 삭제 수행
            return commentDAO.delete(commentId) > 0;
        }
        return false;
    }
}