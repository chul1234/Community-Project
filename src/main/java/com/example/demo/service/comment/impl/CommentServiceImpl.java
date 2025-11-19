package com.example.demo.service.comment.impl;

import java.util.ArrayList; // DB의 comments 테이블과 통하는 CommentDAO
import java.util.HashMap; // 댓글 서비스의 설계도(인터페이스)
import java.util.List; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import java.util.Map; // 이 클래스가 서비스 부품임을 알리는 도구

import org.springframework.beans.factory.annotation.Autowired; // [대댓글 수정] ArrayList 임포트
import org.springframework.stereotype.Service;   // [대댓글 수정] HashMap 임포트

import com.example.demo.dao.CommentDAO; // 여러 데이터를 목록 형태로 다루기 위한 도구
import com.example.demo.service.comment.ICommentService; // 데이터를 '이름표-값' 쌍으로 다루기 위한 도구

@Service
//'CommentServiceImpl'이라는 클래스를 선언하고, 'ICommentService' 설계도를 구현(implements)
public class CommentServiceImpl implements ICommentService {

    @Autowired
    private CommentDAO commentDAO;

    /**
     * [대댓글 수정]
     * DB에서 1차원 목록으로 조회한 댓글들을 계층형 구조(Tree)로 재조립하여 반환합니다.
     */
    @Override 
    @SuppressWarnings("unchecked") // [대댓글 수정] get("replies") 부분의 형변환 경고(노란줄)를 무시하도록 설정
    public List<Map<String, Object>> getCommentsByPostId(int postId) {
        // 1. DAO를 통해 DB에서 'parent_comment_id'가 포함된 1차원 목록을 가져옵니다.
        List<Map<String, Object>> flatList = commentDAO.findByPostId(postId);
        
        // 2. 최종 반환할 계층형 리스트(최상위 댓글만 담김)를 생성합니다.
        List<Map<String, Object>> hierarchicalList = new ArrayList<>();
        
        // 3. 댓글을 ID로 빠르게 찾기 위한 조회용 맵(lookupMap)을 생성합니다.
        Map<Integer, Map<String, Object>> lookupMap = new HashMap<>();

        // 4. (1차 순회) 모든 댓글을 조회용 맵에 넣고, 각 댓글에 자식 댓글(답글)을 담을 'replies' 리스트를 미리 추가합니다.
        for (Map<String, Object> comment : flatList) {
            // 'replies' 키로 빈 ArrayList를 각 댓글 맵에 추가
            comment.put("replies", new ArrayList<Map<String, Object>>());
            // 'comment_id'를 키로 사용하여 조회용 맵에 댓글 맵 자체를 저장
            lookupMap.put((Integer) comment.get("comment_id"), comment);
        }

        // 5. (2차 순회) 부모-자식 관계를 설정합니다.
        for (Map<String, Object> comment : flatList) {
            // 현재 댓글의 부모 ID를 가져옵니다. (최상위 댓글은 null)
            Integer parentId = (Integer) comment.get("parent_comment_id");

            if (parentId != null) {
                // 5-1. 부모 ID가 있는 경우 (대댓글인 경우)
                // 조회용 맵에서 부모 댓글 객체를 찾습니다.
                Map<String, Object> parentComment = lookupMap.get(parentId);
                
                if (parentComment != null) {
                    // 5-1-1. 부모 댓글을 찾았으면, 부모의 'replies' 리스트에 현재 댓글(자식)을 추가합니다.
                    // 이 부분은 @SuppressWarnings("unchecked")로 인해 경고가 표시되지 않습니다.
                    ((List<Map<String, Object>>) parentComment.get("replies")).add(comment);
                } else {
                    // 5-1-2. 부모 ID는 있으나 부모 댓글이 맵에 없는 경우 (예: 부모가 삭제된 고아 댓글)
                    // 이 댓글을 최상위 댓글로 취급하여 hierarchicalList에 추가합니다.
                    hierarchicalList.add(comment);
                }
            } else {
                // 5-2. 부모 ID가 없는 경우 (최상위 댓글인 경우)
                // hierarchicalList에 바로 추가합니다.
                hierarchicalList.add(comment);
            }
        }
        
        // 6. 최상위 댓글 목록(각 댓글은 'replies' 키에 자식 댓글들을 포함)을 반환합니다.
        return hierarchicalList;
    }

    //@Override: 부모 설계도(ICommentService)에 정의된 메소드를 재정의
    @Override
    public Map<String, Object> createComment(int postId, Map<String, Object> comment, String currentUserId) {
        //1. 컨트롤러로부터 받은 댓글 데이터(comment Map)에, 'post_id'라는 이름표(key)로 현재 댓글이 달릴 게시글의 ID(postId)를 추가
        comment.put("post_id", postId);
        // 2. 'user_id'라는 이름표(key)로 현재 로그인한 사용자의 ID(currentUserId)를 추가
        comment.put("user_id", currentUserId);
        
        // 3. [대댓글 수정] 'parent_comment_id'는 컨트롤러에서 'comment' 맵에 담겨서 넘어옵니다.
        //    (예: { "content": "답글입니다", "parent_comment_id": 123 })
        //    만약 'parent_comment_id'가 없으면(최상위 댓글이면) 맵에 해당 키가 없거나 값이 null일 것입니다.
        //    DAO의 save 메소드는 이 맵을 그대로 받아 처리합니다.
        
        // 4. 작성자 정보, 게시글 ID, (선택적)부모 ID가 추가된 완전한 댓글 데이터를 commentDAO의 save 메소드로 전달하여 DB에 저장을 요청
        int affectedRows = commentDAO.save(comment);
        // affectedRows 변수에는 DB에 저장된 행의 수(성공 시 1)가 저장
        
        // 5. (삼항 연산자) 만약 저장된 행의 수가 0보다 크다면(저장 성공), 원본 댓글 데이터를 반환하고,그렇지 않다면(저장 실패), null을 반환
        return affectedRows > 0 ? comment : null;
    }

    @Override
    public Map<String, Object> updateComment(int commentId, Map<String, Object> commentDetails, String currentUserId) {
        // [대댓글 수정] 댓글 내용은 부모/자식 관계없이 수정되므로 기존 로직 그대로 둡니다.
        
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
        // [대댓글 수정] 삭제 로직도 기존과 동일하게 둡니다.
        // DB에 외래 키를 'ON DELETE CASCADE'로 설정했다면, 
        // 부모 댓글 삭제 시 이 로직을 통해 자식 댓글(대댓글)들도 연쇄적으로 자동 삭제됩니다.
        
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