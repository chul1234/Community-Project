// 'BoardController' (게시판 목록)
app.controller('BoardController', function ($scope, $http) {
    $scope.postList = [];
    function fetchPosts() {
        $http.get('/api/posts').then(function(response) {
            $scope.postList = response.data;
        });
    }
    fetchPosts();
});

// 'BoardNewController' (새 글 작성)
app.controller('BoardNewController', function ($scope, $http, $location) {
    $scope.post = { title: '', content: '' };
    $scope.submitPost = function () {
        if (confirm("게시글을 등록하시겠습니까?")) {
            $http.post('/api/posts', $scope.post).then(function () {
                alert("게시글이 성공적으로 등록되었습니다.");
                $location.path('/board');
            }).catch(function(error) {
                alert("게시글 등록에 실패했습니다.");
                console.error("Post creation failed:", error);
            });
        }
    };
});

/**
 * [최종 통합 버전] 게시글 상세 보기, 수정, 삭제 및 댓글을 모두 처리하는 컨트롤러
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce, $rootScope, $location) {
    const postId = $routeParams.postId;
    // --- 게시글 관련 변수 ---
    $scope.post = {};
    $scope.canModify = false;
    $scope.isEditing = false;
    $scope.editData = {};
    // --- 댓글 관련 변수 ---
    $scope.comments = [];
    $scope.newComment = { content: '' };

    // [핵심] 권한 확인 함수
    function checkPermissions() {
        if ($scope.post.user_id && $rootScope.currentUser.role) {
            if ($rootScope.currentUser.role === 'ADMIN' || $scope.post.user_id === $rootScope.currentUser.username) {
                $scope.canModify = true;
            }
        }
    }

    // --- 데이터 로드 ---
    $http.get('/api/posts/' + postId).then(function (response) {
        $scope.post = response.data;
        checkPermissions();
    }).catch(function (error) {
        $location.path('/board');
    });

    function fetchComments() {
        $http.get('/api/posts/' + postId + '/comments').then(function (response) {
            $scope.comments = response.data;
        });
    }
    fetchComments();

    $rootScope.$watch('currentUser.role', function(newRole) {
        if (newRole) {
            checkPermissions();
        }
    });
    
    // --- 게시글 관련 함수 ---
    $scope.switchToEditMode = function() { $scope.isEditing = true; $scope.editData = angular.copy($scope.post); };
    $scope.saveChanges = function() {
        if (confirm("수정하시겠습니까?")) {
            $http.put('/api/posts/' + postId, $scope.editData).then(function(res) {
                $scope.post = res.data; $scope.isEditing = false;
            });
        }
    };
    $scope.cancelEdit = function() { $scope.isEditing = false; };
    $scope.deletePost = function() {
        if (confirm("게시글을 삭제하시겠습니까?")) {
            $http.delete('/api/posts/' + postId).then(() => { $location.path('/board'); });
        }
    };
    $scope.$watch('post.content', function(v) { if(v) $scope.trustedContent = $sce.trustAsHtml(v.replace(/\n/g, '<br/>')); });

    // --- 댓글 관련 함수 ---
    $scope.submitComment = function() {
        $http.post('/api/posts/' + postId + '/comments', $scope.newComment).then(function() {
            $scope.newComment.content = '';
            fetchComments();
        }).catch(function(err) { alert("댓글 등록 실패"); });
    };
    $scope.canModifyComment = function(c) { return $rootScope.currentUser.role === 'ADMIN' || c.user_id === $rootScope.currentUser.username; };
    $scope.deleteComment = function(cId) {
        if (confirm("댓글을 삭제하시겠습니까?")) {
            $http.delete('/api/comments/' + cId).then(() => fetchComments());
        }
    };
    $scope.switchToCommentEditMode = function(c) { c.isEditing = true; c.editContent = c.content; };
    $scope.saveCommentChanges = function(c) {
        $http.put('/api/comments/' + c.comment_id, { content: c.editContent }).then(() => {
            c.isEditing = false; fetchComments();
        });
    };
    $scope.cancelCommentEdit = function(c) { c.isEditing = false; };
});