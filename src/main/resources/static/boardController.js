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
            }).catch(function (error) {
                alert("게시글 등록에 실패했습니다.");
                console.error("Post creation failed:", error);
            });
        }
    };
});

/**
 * [최종 통합 버전] 게시글 상세 보기, 수정, 삭제를 모두 처리하는 컨트롤러
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce, $rootScope, $location) {
    const postId = $routeParams.postId;
    $scope.post = {};
    $scope.canModify = false;
    $scope.isEditing = false;
    $scope.editData = {};

    // [핵심] 권한을 확인하는 함수 (데이터가 모두 준비되면 호출됨)
    function checkPermissions() {
        if ($scope.post.user_id && $rootScope.currentUser.role) {
            if ($rootScope.currentUser.role === 'ADMIN' || $scope.post.user_id === $rootScope.currentUser.username) {
                $scope.canModify = true;
            } else {
                $scope.canModify = false;
            }
        }
    }

    // 1. 게시글 데이터를 먼저 불러옵니다.
    $http.get('/api/posts/' + postId).then(function (response) {
        $scope.post = response.data;
        checkPermissions();
    }).catch(function (error) {
        alert("게시글을 불러오는 데 실패했습니다.");
        $location.path('/board');
    });

    // 2. [핵심] 사용자 역할 정보가 도착하는 것을 감시합니다.
    const unwatch = $rootScope.$watch('currentUser.role', function(newRole) {
        if (newRole) {
            checkPermissions();
        }
    });
    
    // 3. '수정' 버튼 클릭 시
    $scope.switchToEditMode = function() {
        $scope.isEditing = true;
        $scope.editData = angular.copy($scope.post);
    };

    // 4. '수정 완료' 버튼 클릭 시
    $scope.saveChanges = function() {
        if (confirm("게시글을 수정하시겠습니까?")) {
            $http.put('/api/posts/' + postId, $scope.editData).then(function(response) {
                alert("게시글이 성공적으로 수정되었습니다.");
                $scope.post = response.data;
                $scope.isEditing = false;
            }).catch(function(error) {
                alert("게시글 수정에 실패했습니다.");
            });
        }
    };

    // 5. '취소' 버튼 클릭 시
    $scope.cancelEdit = function() {
        $scope.isEditing = false;
    };

    // 6. '삭제' 버튼 클릭 시
    $scope.deletePost = function() {
        if (confirm("게시글을 정말 삭제하시겠습니까?")) {
            $http.delete('/api/posts/' + postId).then(function() {
                alert("게시글이 삭제되었습니다.");
                $location.path('/board');
            }).catch(function(error) {
                alert("게시글 삭제에 실패했습니다.");
            });
        }
    };
    
    // 7. 줄바꿈 처리 로직
    $scope.$watch('post.content', function(value) {
        if (value) {
            $scope.trustedContent = $sce.trustAsHtml(value.replace(/\n/g, '<br/>'));
        }
    });
});