// 'BoardController' (게시판 목록)
// 게시글 목록을 불러오는 기능
app.controller('BoardController', function ($scope, $http) {
    //$scope 컨트롤러 와 뷰 연결, $http 백엔드와 HTTP통신
    $scope.postList = []; // 게시글 목록 변수 (배열 초기화)
    function fetchPosts() { // 게시글 목록을 서버에서 불러오는 함수
        $http.get('/api/posts').then(function(response) { // GET 요청
            //.then 성공적으로 응답오면 실행, response 서버개 보낸 정보 담는 객체
            $scope.postList = response.data; // 응답 데이터를 postList에 저장
            //response.data : 서버가 보낸 실제 데이터(게시글 목록)
        });
    }
    fetchPosts(); // 컨트롤러가 로드될 때 게시글 목록을 즉시 불러옴
});

// 'BoardNewController' (새 글 작성)

app.controller('BoardNewController', function ($scope, $http, $location) {
    // $scope: 뷰와 컨트롤러 연결, $http: 백엔드와 HTTP 통신, $location: 페이지 이동 제어
    $scope.post = { title: '', content: '' }; // 새 게시글 데이터를 담는 객체 초기화
    // post 객체는 title(제목)과 content(내용) 속성을 가짐
    // 게시글 등록 함수
    $scope.submitPost = function () {
        // 사용자에게 게시글 등록 여부 확인
        if (confirm("게시글을 등록하시겠습니까?")) {
            //$http.post: HTTP POST 데이터 전송
            //$scope.post: 객체 JSON 형태로 변환 서버 전송
            $http.post('/api/posts', $scope.post).then(function () {
                // 성공 시 알림 및 게시판 목록 페이지로 이동
                alert("게시글이 성공적으로 등록되었습니다.");
                $location.path('/board'); // 게시판 목록 페이지로 이동
            }).catch(function(error) { // 오류 처리
                alert("게시글 등록에 실패했습니다."); // 오류 알림
                console.error("Post creation failed:", error); // 오류 로그 출력
            });
        }
    };
});

/**
 * 게시글 상세 보기, 수정, 삭제 및 댓글을 모두 처리하는 컨트롤러
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce, $rootScope, $location) {
   // $routeParams: URL 파라미터 접근, $sce: 신뢰할 수 있는 콘텐츠 처리, $rootScope: 공유되는 전역 저장 공간(사용자 정보)
    const postId = $routeParams.postId; // URL에서 postId 파라미터 추출
    // --- 게시글 관련 변수 ---
    $scope.post = {}; // 게시글 데이터 객체
    $scope.canModify = false; // 수정/삭제 권한 여부
    $scope.isEditing = false; // 수정 모드 여부
    $scope.editData = {}; // 수정 중인 게시글 데이터
    // --- 댓글 관련 변수 ---
    $scope.comments = [];// 댓글 목록
    $scope.newComment = { content: '' }; // 새 댓글 데이터

    // [핵심] 권한 확인 함수
    // 게시글 및 댓글 수정/삭제 권한
    function checkPermissions() { 
        // 게시글 정보와 사용자 정보 로드확인
        if ($scope.post.user_id && $rootScope.currentUser.role) {
            // 데이터 로딩 순서 문제(타이밍 문제)를 해결하기 위해 두 정보가 모두 있을 때만 권한
            if ($rootScope.currentUser.role === 'ADMIN' || $scope.post.user_id === $rootScope.currentUser.username) {
               // 현재 로그인이 관리자 이거나 게시글 작성자 ID가 현재 사용자 같다면 
                $scope.canModify = true; // 수정/삭제 권한 부여
            } else {
                // 권한이 없으면 false로 설정
                $scope.canModify = false;
            }
        }
    }

    // --- 데이터 로드 ---
    //게시글 데이터 서버 가져옴
    // $http.get. 주소에 get요청
    // boardcontroller.java의 @getmapping으로 메소드 호출
    $http.get('/api/posts/' + postId).then(function (response) {
        $scope.post = response.data; //성공시 받은 데이터 $scope.post에 저장
        checkPermissions(); //게시글 데이터 도착, 권한 부여
    }).catch(function (error) {
        $location.path('/board');
    });

    // 댓글 목록 가져오는 함수
    function fetchComments() { 
        // 주소에 get요청
        $http.get('/api/posts/' + postId + '/comments').then(function (response) {
            $scope.comments = response.data; //성고시 받은 댓글 목록$scope.comments에 저장
        });
    }
    fetchComments();

    //[핵심] $rootScope.currentUser.role 값이 바뀔 때마다 자동으로 함수를 실행
    $rootScope.$watch('currentUser.role', function(newRole) {
        // 사용자 정보가 게시글 정보보다 늦게 도착하는 '타이밍' 문제를 해결
        // newRole 사용자 정보 도착
        if (newRole) {
            checkPermissions(); //권한 다시 확인
        }
    });
    
    // --- 게시글 관련 함수들 ---
    // 게시글 '수정' 버튼(ng-click="switchToEditMode()") 클릭 시 실행될 함수
    $scope.switchToEditMode = function() {
        $scope.isEditing = true; // '수정 모드'로 변경 (HTML에서 ng-if="isEditing" 부분이 보이게 됨)
        // angular.copy(): 원본($scope.post)을 깊은 복사하여 수정 중 취소해도 원본이 변경되지 않도록 함
        $scope.editData = angular.copy($scope.post);
    };
    // 게시글 '수정 완료' 버튼(ng-click="saveChanges()") 클릭 시 실행될 함수
    $scope.saveChanges = function() {
        if (confirm("수정하시겠습니까?")) {
            // $http.put으로 '/api/posts/{postId}' 주소에 수정된 데이터($scope.editData)를 PUT 방식으로 전송
            // 사용처: 백엔드 BoardController.java의 @PutMapping("/api/posts/{postId}") 메소드 호출
            $http.put('/api/posts/' + postId, $scope.editData).then(function(res) {
                // 성공 시, 서버로부터 받은 최신 데이터(res.data)로 $scope.post를 업데이트하고,
                $scope.post = res.data;
                // '보기 모드'로 전환합니다.
                $scope.isEditing = false;
            });
        }
    };
    // 게시글 수정 '취소' 버튼(ng-click="cancelEdit()") 클릭 시 실행될 함수입니다. '보기 모드'로 전환
    $scope.cancelEdit = function() { $scope.isEditing = false; };
    // 게시글 '삭제' 버튼(ng-click="deletePost()") 클릭 시 실행될 함수
    $scope.deletePost = function() {
        if (confirm("게시글을 삭제하시겠습니까?")) {
            // $http.delete로 '/api/posts/{postId}' 주소에 DELETE 요청을 보냄
            // 사용처: 백엔드 BoardController.java의 @DeleteMapping("/api/posts/{postId}") 메소드 호출
            $http.delete('/api/posts/' + postId).then(() => {
                // 성공 시, 목록 페이지('/board')로 이동합니다.
                $location.path('/board');
            });
        }
    };
    // 게시글 내용($scope.post.content)이 변경될 때마다 감시($watch)하여 실행되는 함수
    // HTML 렌더링을 위해 줄바꿈 문자(\n)를 HTML 태그(<br/>)로 변환하고,
    // $sce.trustAsHtml()을 사용하여 AngularJS가 이 HTML을 안전하게 화면에 표시하도록 처리
    // 결과를 $scope.trustedContent 변수에 저장
    // 사용처: board-detail.html의 ng-bind-html="trustedContent" 부분에서 사용
    $scope.$watch('post.content', function(v) { if(v) $scope.trustedContent = $sce.trustAsHtml(v.replace(/\n/g, '<br/>')); });

    // --- 댓글 관련 함수들 ---
    // '댓글 등록' 버튼(ng-click="submitComment()") 클릭 시 실행될 함수
    $scope.submitComment = function() {
        // $http.post로 '/api/posts/{postId}/comments' 주소에 새 댓글 내용($scope.newComment)을 POST 전송
        // 사용처: 백엔드 CommentController.java의 @PostMapping("/api/posts/{postId}/comments") 메소드 호출
        $http.post('/api/posts/' + postId + '/comments', $scope.newComment).then(function() {
            // 성공 시, 입력창($scope.newComment.content)을 비우고,
            $scope.newComment.content = '';
            // 댓글 목록을 다시 불러와 화면을 갱신합니다.
            fetchComments();
        }).catch(function(err) { alert("댓글 등록 실패"); }); // 실패 시 알림창
    };
    // (HTML에서 ng-if="canModifyComment(comment)"로 사용) 댓글의 수정/삭제 권한을 확인하는 함수
    $scope.canModifyComment = function(c) {
        // 현재 사용자가 관리자(ADMIN)이거나(||) 댓글 작성자(c.user_id) 본인일 경우 true를 반환
        return $rootScope.currentUser.role === 'ADMIN' || c.user_id === $rootScope.currentUser.username;
    };
    // 댓글 '삭제' 버튼(ng-click="deleteComment(comment.comment_id)") 클릭 시 실행될 함수.
    $scope.deleteComment = function(cId) {
        if (confirm("댓글을 삭제하시겠습니까?")) {
            // $http.delete로 '/api/comments/{commentId}' 주소에 DELETE 요청을 보냄
            // 사용처: 백엔드 CommentController.java의 @DeleteMapping("/api/comments/{commentId}") 메소드 호출
            $http.delete('/api/comments/' + cId).then(() => fetchComments()); // 성공 시 목록 새로고침
        }
    };
    // 댓글 '수정' 버튼(ng-click="switchToCommentEditMode(comment)") 클릭 시 실행될 함수
    $scope.switchToCommentEditMode = function(c) {
        c.isEditing = true; // 해당 댓글 객체의 isEditing 상태를 true로 변경 (수정 폼이 보이게 됨)
        c.editContent = c.content; // 원본 내용을 수정용 변수(editContent)에 복사
    };
    // 댓글 '저장' 버튼(ng-click="saveCommentChanges(comment)") 클릭 시 실행될 함수
    $scope.saveCommentChanges = function(c) {
        // $http.put으로 '/api/comments/{commentId}' 주소에 수정된 내용({ content: c.editContent })을 PUT 전송
        // 사용처: 백엔드 CommentController.java의 @PutMapping("/api/comments/{commentId}") 메소드 호출
        $http.put('/api/comments/' + c.comment_id, { content: c.editContent }).then(() => {
            c.isEditing = false; // 성공 시 '보기 모드'로 전환하고,
            fetchComments(); // 목록을 새로고침
        });
    };
    // 댓글 수정 '취소' 버튼(ng-click="cancelCommentEdit(comment)") 클릭 시 실행될 함수입니다. '보기 모드'로 전환
    $scope.cancelCommentEdit = function(c) { c.isEditing = false; };
});