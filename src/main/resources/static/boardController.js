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

/**
 * 게시글 작성 페이지(/board/new)를 제어하는 컨트롤러
 */
app.controller('BoardNewController', function ($scope, $http, $location) {
    // 폼 데이터를 담을 빈 객체를 만듭니다.
    $scope.post = {
        title: '',
        content: ''
    };

    // '게시글 등록' 버튼을 눌렀을 때 실행될 함수입니다.
    $scope.submitPost = function () {
        // 1. 사용자에게 정말 등록할 것인지 확인합니다.
        if (confirm("게시글을 등록하시겠습니까?")) {
            // 2. 백엔드 API('/api/posts')로 제목과 내용을 POST 방식으로 전송합니다.
            $http.post('/api/posts', $scope.post).then(function () {
                // 3. 성공적으로 등록되면, 알림을 띄우고 게시판 목록 페이지로 이동합니다.
                alert("게시글이 성공적으로 등록되었습니다.");
                $location.path('/board');
            }).catch(function (error) {
                // 4. 실패하면 에러 메시지를 보여줍니다.
                alert("게시글 등록에 실패했습니다.");
                console.error("Post creation failed:", error);
            });
        }
    };
});

/**
 * 게시글 상세 보기 페이지(/board/:postId)를 제어하는 컨트롤러
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce) {
    // URL에서 게시글 ID를 가져옵니다. (예: #!/board/1 -> postId는 1)
    const postId = $routeParams.postId;
    $scope.post = {}; // 게시글 데이터를 담을 빈 객체

    // 백엔드에 특정 게시글 데이터를 요청합니다.
    $http.get('/api/posts/' + postId).then(function (response) {
        $scope.post = response.data;
    }).catch(function (error) {
        alert("게시글을 불러오는 데 실패했습니다.");
        console.error(error);
    });

    // AngularJS에서 줄바꿈(\n)을 <br> 태그로 변환해주는 필터
    // (ng-bind-html과 함께 사용)
    $scope.$watch('post.content', function(value) {
        if (value) {
            $scope.trustedContent = $sce.trustAsHtml(value.replace(/\n/g, '<br/>'));
        }
    });
});