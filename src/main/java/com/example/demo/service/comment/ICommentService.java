// 파일: ICommentService.java (수정 필요 없음)
package com.example.demo.service.comment;

import java.util.List;
import java.util.Map;

public interface ICommentService {
    // 1. 이 메소드는 그대로 List<Map<String, Object>>를 반환합니다.
    //    (구현체(ServiceImpl)에서 평평한 목록이 아닌 계층형 목록을 반환하도록 바꿀 것입니다.)
    List<Map<String, Object>> getCommentsByPostId(int postId);
    
    // 2. 이 메소드도 수정할 필요가 없습니다.
    //    (Service 구현체에서 comment Map 안에 'parent_comment_id'가 있는지 확인하여 처리할 것입니다.)
    Map<String, Object> createComment(int postId, Map<String, Object> comment, String currentUserId);
    
    // 3. 댓글 내용은 parent_comment_id와 상관없이 수정되므로 그대로 둡니다.
    Map<String, Object> updateComment(int commentId, Map<String, Object> commentDetails, String currentUserId);
    
    // 4. 댓글 삭제는 commentId로만 처리되므로 그대로 둡니다.
    boolean deleteComment(int commentId, String currentUserId, List<String> roles);
}