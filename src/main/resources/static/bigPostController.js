app.controller('BigPostController', function ($scope, $http) {
    $scope.postList = [];
    $scope.currentPage = 1;
    $scope.totalPages = 0;
    $scope.totalItems = 0;
    $scope.pageSize = 1000; // 20개씩 보기 -> 1000개씩 보기로 변경 // 수정됨

    // ▼ 키셋 페이징용 상태값들 // 수정됨
    $scope.pagesCache = {};      // 페이지별 데이터 캐시 (1페이지, 2페이지...) // 수정됨
    $scope.lastIdForPage = {};   // 각 페이지의 마지막 post_id 저장 // 수정됨

    // 총 게시글 수 / 총 페이지 수 불러오기 (COUNT(*)는 여기서만 사용) // 수정됨
    function loadTotalInfo() { // 수정됨
        // size=1로 요청해서 totalItems만 사용 (데이터는 버림) // 수정됨
        $http.get('/api/big-posts', {
            params: { page: 1, size: 1 } // 수정됨
        }).then(function (response) {
            $scope.totalItems = response.data.totalItems;          // 전체 건수 // 수정됨
            $scope.totalPages = Math.ceil($scope.totalItems / $scope.pageSize); // 수정됨
        }).catch(function (err) {
            console.error("총 개수 조회 실패:", err);
        });
    }

    // 첫 페이지(1페이지) 로드: /api/big-posts/first 사용 // 수정됨
    function loadFirstPage() { // 수정됨
        $http.get('/api/big-posts/first', {
            params: { size: $scope.pageSize } // 수정됨
        }).then(function (response) {
            $scope.postList = response.data;  // List<Map> 그대로 옴 // 수정됨
            $scope.currentPage = 1;           // 1페이지 // 수정됨

            // 1페이지 캐시에 저장 // 수정됨
            $scope.pagesCache[1] = response.data; // 수정됨

            if ($scope.postList && $scope.postList.length > 0) {   // 수정됨
                var lastPost = $scope.postList[$scope.postList.length - 1]; // 수정됨
                $scope.lastIdForPage[1] = lastPost.post_id;        // 수정됨
            }
        }).catch(function (err) {
            console.error("첫 페이지 로드 실패:", err); // 수정됨
        });
    }

    // 페이지 이동 함수 (이전/다음 전용) // 수정됨
    $scope.goToPage = function (page) { // 수정됨
        // 범위 체크
        if (page < 1 || ($scope.totalPages > 0 && page > $scope.totalPages)) {
            return;
        }

        // 같은 페이지로 이동 요청이면 무시
        if (page === $scope.currentPage) {
            return;
        }

        // [이전 페이지] : currentPage - 1 // 수정됨
        if (page === $scope.currentPage - 1) { // 수정됨
            var cachedPrev = $scope.pagesCache[page]; // 수정됨
            if (cachedPrev) { // 수정됨
                $scope.postList = cachedPrev; // 수정됨
                $scope.currentPage = page;    // 수정됨
            }
            return;
        }

        // [다음 페이지] : currentPage + 1 만 허용 // 수정됨
        if (page === $scope.currentPage + 1) { // 수정됨
            // 이미 캐시에 있는 경우 (이전에 한 번 로드한 페이지)
            var cachedNext = $scope.pagesCache[page]; // 수정됨
            if (cachedNext) { // 수정됨
                $scope.postList = cachedNext; // 수정됨
                $scope.currentPage = page;    // 수정됨
                return;
            }

            // 캐시에 없으면 서버에서 새로 로드 (/api/big-posts/next) // 수정됨
            var lastId = $scope.lastIdForPage[$scope.currentPage]; // 수정됨
            if (!lastId) { // 수정됨
                return;
            }

            $http.get('/api/big-posts/next', { // 수정됨
                params: {
                    lastId: lastId,
                    size: $scope.pageSize
                }
            }).then(function (response) {
                var data = response.data || []; // 수정됨

                if (data.length === 0) { // 더 이상 다음 페이지 없음 // 수정됨
                    return;
                }

                $scope.postList = data;  // 화면 교체 // 수정됨
                $scope.currentPage = page; // 수정됨

                // 캐시에 저장 // 수정됨
                $scope.pagesCache[page] = data; // 수정됨

                // 이 페이지의 마지막 post_id 저장 // 수정됨
                var lastPost = data[data.length - 1]; // 수정됨
                $scope.lastIdForPage[page] = lastPost.post_id; // 수정됨
            }).catch(function (err) {
                console.error("다음 페이지 로드 실패:", err); // 수정됨
            });

            return;
        }

        // 그 외 (예: 1 → 3 같이 점프)는 지원하지 않음 (키셋 페이징 특성) // 수정됨
    };

    // 컨트롤러 시작 시: 총 개수 + 첫 페이지 로드 // 수정됨
    loadTotalInfo(); // 수정됨
    loadFirstPage(); // 수정됨
});
