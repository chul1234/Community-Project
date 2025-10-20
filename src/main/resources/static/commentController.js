/**
 * 댓글 기능을 전담하는 컨트롤러
 */
app.controller('CommentController', function ($scope, $http, $routeParams, $rootScope) {
    const postId = $routeParams.postId;
    $scope.comments = []; // 댓글 목록
    $scope.newComment = { content: '' }; // 새 댓글 데이터

    // 1. 댓글 목록을 불러오는 함수
    function fetchComments() {
        $http.get('/api/posts/' + postId + '/comments').then(function (response) {
            $scope.comments = response.data;
        });
    }
    fetchComments(); // 페이지 시작 시 댓글 로드

    // 2. 새 댓글 등록
    $scope.submitComment = function() {
        $http.post('/api/posts/' + postId + '/comments', $scope.newComment).then(function() {
            $scope.newComment.content = '';
            fetchComments();
        })
        // ▼▼▼▼▼ [추가] 서버에서 에러 응답이 왔을 때 실행될 .catch() 블록 ▼▼▼▼▼
        .catch(function(error) {
            alert("댓글 등록에 실패했습니다. 다시 시도해주세요.");
            console.error("Comment creation failed:", error);
        });
    };

    // 3. 댓글 수정/삭제 권한 확인
    $scope.canModifyComment = function(comment) {
        const currentUser = $rootScope.currentUser;
        if (!currentUser || !currentUser.role) return false;
        // 관리자이거나, 댓글 작성자 본인일 경우
        return currentUser.role === 'ADMIN' || comment.user_id === currentUser.username;
    };
    
    // 4. 댓글 삭제
    $scope.deleteComment = function(commentId) {
        if (confirm("댓글을 정말 삭제하시겠습니까?")) {
            $http.delete('/api/comments/' + commentId).then(function() {
                alert("댓글이 삭제되었습니다.");
                fetchComments(); // 댓글 목록 새로고침
            }).catch(function() {
                alert("댓글 삭제에 실패했습니다. (권한 없음)");
            });
        }
    };
    
    // 5. 댓글 수정 모드로 전환
    $scope.switchToCommentEditMode = function(comment) {
        comment.isEditing = true;
        comment.editContent = comment.content; // 수정용 데이터 복사
    };

    // 6. 댓글 수정 저장
    $scope.saveCommentChanges = function(comment) {
        $http.put('/api/comments/' + comment.comment_id, { content: comment.editContent }).then(function() {
            alert("댓글이 수정되었습니다.");
            comment.isEditing = false;
            fetchComments(); // 댓글 목록 새로고침
        }).catch(function() {
            alert("댓글 수정에 실패했습니다. (권한 없음)");
        });
    };
    
    // 7. 댓글 수정 취소
    $scope.cancelCommentEdit = function(comment) {
        comment.isEditing = false;
    };
});