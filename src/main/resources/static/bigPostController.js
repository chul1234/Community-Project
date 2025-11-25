// 수정됨: BigPostController 전체에 라인별 주석 추가

app.controller('BigPostController', function ($scope, $http) {

    $scope.postList = [];              // 현재 화면에 보여줄 게시글 목록
    $scope.currentPage = 1;            // 현재 페이지 번호(키셋 페이징에서 UI용)
    $scope.totalPages = 0;             // 전체 페이지 수 (총 개수 / pageSize)
    $scope.totalItems = 0;             // 전체 게시글 수
    $scope.pageSize = 1000;            // 한 번에 로드할 게시글 수 (기본 1000)

    // ▼ 키셋 페이징을 위한 캐싱 구조
    $scope.pagesCache = {};            // 페이지별 데이터 캐시 (1페이지 → 데이터)
    $scope.lastIdForPage = {};         // 각 페이지의 마지막 post_id 저장 (키셋 이동용)

    // ------------------------------------------
    // 총 게시글 수 / 총 페이지 수 조회 (COUNT(*)는 여기에서만 1번 사용)
    // ------------------------------------------
    function loadTotalInfo() {
        // page=1, size=1로 요청해서 totalItems(전체 건수)만 받아옴
        $http
            .get('/api/big-posts', {
                params: { page: 1, size: 1 },   // DB 부하 최소화 목적
            })
            .then(function (response) {
                $scope.totalItems = response.data.totalItems;      // 전체 게시글 수 저장
                $scope.totalPages = Math.ceil($scope.totalItems / $scope.pageSize); // 총 페이지 계산
            })
            .catch(function (err) {
                console.error('총 개수 조회 실패:', err);         // 에러 로그 출력
            });
    }

    // ------------------------------------------
    // 첫 페이지 로드 (키셋 페이징: /api/big-posts/first)
    // ------------------------------------------
    function loadFirstPage() {
        $http
            .get('/api/big-posts/first', {
                params: { size: $scope.pageSize },                // 1,000개 요청
            })
            .then(function (response) {
                $scope.postList = response.data;                  // 첫 페이지 데이터 목록 저장
                $scope.currentPage = 1;                           // 현재 페이지 = 1

                $scope.pagesCache[1] = response.data;             // 첫 페이지를 캐시에 저장

                // 현재 페이지 마지막 post_id 저장
                if ($scope.postList && $scope.postList.length > 0) {
                    var lastPost = $scope.postList[$scope.postList.length - 1]; // 배열 마지막 요소
                    $scope.lastIdForPage[1] = lastPost.post_id;                 // 첫 페이지 마지막 ID 저장
                }
            })
            .catch(function (err) {
                console.error('첫 페이지 로드 실패:', err);       // 에러 출력
            });
    }

    // ------------------------------------------
    // 페이지 이동 (키셋 페이징: 이전/다음만 허용)
    // ------------------------------------------
    $scope.goToPage = function (page) {

        // 페이지 범위 제한 (1보다 작거나 totalPages보다 크면 오류)
        if (page < 1 || ($scope.totalPages > 0 && page > $scope.totalPages)) {
            return;                                              // 잘못된 페이지 → 무시
        }

        // 같은 페이지 이동 요청이면 무시
        if (page === $scope.currentPage) {
            return;
        }

        // ------------------------------
        // [이전 페이지] (currentPage - 1)
        // ------------------------------
        if (page === $scope.currentPage - 1) {
            var cachedPrev = $scope.pagesCache[page];            // 이전 페이지 캐시 가져오기
            if (cachedPrev) {
                $scope.postList = cachedPrev;                    // 캐시된 데이터 화면에 표시
                $scope.currentPage = page;                       // 현재 페이지 업데이트
            }
            return;
        }

        // ------------------------------
        // [다음 페이지] (currentPage + 1)
        // ------------------------------
        if (page === $scope.currentPage + 1) {

            // 이미 next 페이지가 캐시에 있다면 바로 사용
            var cachedNext = $scope.pagesCache[page];
            if (cachedNext) {
                $scope.postList = cachedNext;                    // 캐시 데이터 표시
                $scope.currentPage = page;                       // 페이지 번호 변경
                return;
            }

            // 캐시에 없다면 새로 서버에서 로드
            var lastId = $scope.lastIdForPage[$scope.currentPage]; // 현재 페이지 마지막 post_id
            if (!lastId) {
                return;                                          // lastId 없으면 로드 불가
            }

            $http
                .get('/api/big-posts/next', {
                    params: {
                        lastId: lastId,                           // 키셋 기준점
                        size: $scope.pageSize,                    // 1000개 요청
                    },
                })
                .then(function (response) {
                    var data = response.data || [];               // 받아온 데이터

                    if (data.length === 0) {
                        return;                                   // 더 이상 다음 페이지 없으면 종료
                    }

                    $scope.postList = data;                       // 화면에 새 데이터 표시
                    $scope.currentPage = page;                    // 페이지 번호 변경

                    $scope.pagesCache[page] = data;               // 캐시에 저장

                    // 이 페이지의 마지막 post_id 기록
                    var lastPost = data[data.length - 1];
                    $scope.lastIdForPage[page] = lastPost.post_id;
                })
                .catch(function (err) {
                    console.error('다음 페이지 로드 실패:', err); // 에러 출력
                });

            return;
        }

        // ------------------------------
        // 그 외 점프 이동(1→3 등)은 지원하지 않음
        // 키셋 페이징 특성: 이전/다음만 이동 가능
        // ------------------------------
    };

    // ------------------------------------------
    // 컨트롤러 초기 실행: 전체 건수 + 첫 페이지 로드
    // ------------------------------------------
    loadTotalInfo();   // 총 개수/전체 페이지 계산
    loadFirstPage();   // 실제 첫 페이지 데이터 로드
});

// 수정됨 끝
