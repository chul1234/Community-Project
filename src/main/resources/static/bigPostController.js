app.controller('BigPostController', function ($scope, $http) {
    $scope.postList = [];
    $scope.currentPage = 1;
    $scope.totalPages = 0;
    $scope.totalItems = 0;
    $scope.pageSize = 20; // 20개씩 보기

    // 데이터 불러오기 함수
    function fetchPosts(page) {
        $http.get('/api/big-posts', {
            params: { page: page, size: $scope.pageSize }
        }).then(function (response) {
            $scope.postList = response.data.posts;
            $scope.totalItems = response.data.totalItems;
            $scope.totalPages = response.data.totalPages;
            $scope.currentPage = response.data.currentPage;
        }).catch(function(err) {
            console.error("API 요청 실패:", err);
        });
    }

    // 페이지 이동 함수
    $scope.goToPage = function (page) {
        if (page >= 1 && page <= $scope.totalPages) {
            fetchPosts(page);
        }
    };

    // 컨트롤러 시작 시 1페이지 로드
    fetchPosts(1);
});