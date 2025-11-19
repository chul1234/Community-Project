/**
 * 댓글 기능을 전담하는 컨트롤러
 */
// 'CommentController' 등록. board-detail.html 댓글 영역과 연결됨.
// app.controller(컨트롤러이름, function(주입될의존성들){...}): 새 컨트롤러 정의.
app.controller('CommentController', function ($scope, $http, $routeParams, $rootScope) {
    // $scope: HTML(뷰)-컨트롤러(로직) 데이터 연결 객체. 뷰와 데이터 공유.
    // $http: 서버와 HTTP 통 서비스. API 호출용.
    // $routeParams: URL 경로의 파라미터 값 접근 서비스. (예: /board/:postId 에서 postId 값 추출)
    // $rootScope: 전역 $scope. 앱 전체에서 데이터 공유. (로그인 정보 등)

    // $routeParams.postId: 현재 URL에서 ':postId' 부분 값(현재 게시글 ID) 가져옴.
    const postId = $routeParams.postId;
    // $scope.comments: 댓글 목록 저장할 빈 배열. HTML ng-repeat에서 사용됨.
    $scope.comments = [];
    // $scope.newComment: 새 댓글 입력 내용 저장할 객체. HTML 입력창(ng-model)과 연결됨.
    $scope.newComment = { content: '' };

    // --- 함수 정의 ---
    // 1. fetchComments 함수: 현재 게시글(postId)의 댓글 목록 서버에서 불러오는 내부 함수.
    function fetchComments() {
        // $http.get(URL): 지정된 URL로 GET 요청 보냄. 비동기 처리.
        // then(성공콜백): 요청 성공 시 실행될 함수 정의.
        // 사용처: 백엔드 CommentController.java @GetMapping("/api/posts/{postId}/comments") 호출.
        $http.get('/api/posts/' + postId + '/comments').then(function (response) {
            // response.data: 서버 응답 데이터(댓글 목록 배열).
            // $scope.comments 업데이트 -> AngularJS가 자동으로 HTML 갱.
            $scope.comments = response.data;
        });
    }
    // fetchComments(): 컨트롤러 시작 시 댓글 목록 바로 로드하기 위해 함수 호출.
    fetchComments();

    // 2. submitComment 함수: '댓글 등록' 버튼 클릭 시 실행될 함수. $scope에 정의해야 HTML(ng-click)에서 호출 가능.
    $scope.submitComment = function () {
        // $http.post(URL, 데이터): 지정된 URL로 데이터를 POST 방식으로 전송.
        // $scope.newComment 객체가 JSON으로 변환되어 서버로 전송됨.
        // 사용처: 백엔드 CommentController.java @PostMapping("/api/posts/{postId}/comments") 호출.
        $http
            .post('/api/posts/' + postId + '/comments', $scope.newComment)
            .then(function () {
                // 성공 시(.then):
                // $scope.newComment.content 초기화 -> HTML 입력창 비워짐.
                $scope.newComment.content = '';
                // fetchComments() 호출하여 댓글 목록 새로고침.
                fetchComments();
            })
            // catch(실패콜백): 요청 실패 시 실행될 함수 정의.
            .catch(function (error) {
                // alert(): 브라우저 알림창 표시.
                alert('댓글 등록에 실패했습니다. 다시 시도해주세요.');
                // console.error(): 개발자 도구 콘솔에 에러 메시지 출력.
                console.error('Comment creation failed:', error);
            });
    };

    // 3. canModifyComment 함수: 댓글 수정/삭제 권한 확인 함수. $scope에 정의되어 HTML(ng-if)에서 호출됨.
    // comment: HTML ng-repeat에서 반복 중인 현재 댓글 객체.
    $scope.canModifyComment = function (comment) {
        // $rootScope.currentUser: MainController에서 설정한 전역 로그인 사용자 정보 객체.
        const currentUser = $rootScope.currentUser;
        // 사용자 정보 없으면 false 반환 (권한 없음).
        if (!currentUser || !currentUser.role) return false;
        // 현재 사용자가 'ADMIN'이거나(||) 댓글 작성자(comment.user_id) 본인($rootScope.currentUser.username)이면 true 반환.
        return currentUser.role === 'ADMIN' || comment.user_id === currentUser.username;
    };

    // 4. deleteComment 함수: 댓글 '삭제' 버튼 클릭 시 실행될 함수.
    // commentId: HTML ng-click에서 전달된 삭제할 댓글의 ID.
    $scope.deleteComment = function (commentId) {
        // confirm(): 브라우저 확인창 표시. '확인' 시 true 반환.
        if (confirm('댓글을 정말 삭제하시겠습니까?')) {
            // $http.delete(URL): 지정된 URL로 DELETE 요청 보냄.
            // 사용처: 백엔드 CommentController.java @DeleteMapping("/api/comments/{commentId}") 호출.
            $http
                .delete('/api/comments/' + commentId)
                .then(function () {
                    // 성공 시 알림창 표시.
                    alert('댓글이 삭제되었습니다.');
                    // 댓글 목록 새로고침.
                    fetchComments();
                })
                .catch(function () {
                    // 실패 시 알림창 표시 (주로 권한 없음).
                    alert('댓글 삭제에 실패했습니다. (권한 없음)');
                });
        }
    };

    // 5. switchToCommentEditMode 함수: 댓글 '수정' 버튼 클릭 시 실행 (수정 모드 전환용).
    // comment: HTML ng-click에서 전달된 수정할 댓글 객체.
    $scope.switchToCommentEditMode = function (comment) {
        // comment 객체에 isEditing 속성 추가/수정하여 true로 설정.
        // 사용처: HTML ng-if="comment.isEditing"에서 수정 폼 표시 여부 결정.
        comment.isEditing = true;
        // comment 객체에 editContent 속성 추가/수정하여 원본 내용을 복사.
        // 사용처: HTML 수정 <textarea ng-model="comment.editContent">와 연결됨.
        comment.editContent = comment.content;
    };

    // 6. saveCommentChanges 함수: 댓글 '저장' 버튼 클릭 시 실행.
    // comment: HTML ng-click에서 전달된 수정 중인 댓글 객체.
    $scope.saveCommentChanges = function (comment) {
        // $http.put(URL, 데이터): 지정된 URL로 데이터를 PUT 방식으로 전송 (수정 요청).
        // { content: comment.editContent }: 수정된 내용만 객체로 만들어 전송.
        // 사용처: 백엔드 CommentController.java @PutMapping("/api/comments/{commentId}") 호출.
        $http
            .put('/api/comments/' + comment.comment_id, { content: comment.editContent })
            .then(function () {
                // 성공 시 알림창 표시.
                alert('댓글이 수정되었습니다.');
                // isEditing 속성을 false로 설정하여 보기 모드로 전환.
                comment.isEditing = false;
                // 댓글 목록 새로고침.
                fetchComments();
            })
            .catch(function () {
                // 실패 시 알림창 표시 (주로 권한 없음).
                alert('댓글 수정에 실패했습니다. (권한 없음)');
            });
    };

    // 7. cancelCommentEdit 함수: 댓글 수정 '취소' 버튼 클릭 시 실행.
    // comment: HTML ng-click에서 전달된 수정 중인 댓글 객체.
    $scope.cancelCommentEdit = function (comment) {
        // isEditing 속성을 false로 설정하여 보기 모드로 전환.
        comment.isEditing = false;
    };
}); // CommentController 정의 끝.
