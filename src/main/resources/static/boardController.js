// 'BoardController'라는 이름의 새 컨트롤러를 정의합니다.
app.controller('BoardController', function ($scope, $http) {
    // 게시글 목록을 담을 빈 배열을 미리 만듭니다.
    $scope.postList = [];

    // 게시글 목록을 가져오는 함수를 정의합니다.
    function fetchPosts() {
        // [수정] 백엔드에 만들어 둔 /api/posts API를 호출해서 데이터를 가져옵니다.
        $http.get('/api/posts').then(function(response) {
            // 성공적으로 데이터를 받으면, 그 결과를 $scope.postList에 저장합니다.
            // 이 데이터는 board-list.html의 ng-repeat에서 사용됩니다.
            $scope.postList = response.data;
        });
    }

    // 컨트롤러가 시작될 때 게시글 목록을 가져오도록 함수를 호출합니다.
    fetchPosts();
});