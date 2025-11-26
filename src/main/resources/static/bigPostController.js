app.controller('BigPostController', function ($scope, $http, $window) {

    $scope.postList = [];              // 현재 화면에 보여줄 게시글 목록
    $scope.currentPage = 1;            // 현재 페이지 번호(키셋 페이징에서 UI용)
    $scope.totalPages = 0;             // 전체 페이지 수 (총 개수 / pageSize)
    $scope.totalItems = 0;             // 전체 게시글 수
    $scope.pageSize = 1000;            // 한 번에 로드할 게시글 수 (서버 요청 기준, 기본 1000)

    // ▼ 사용자가 입력창에서 조정할 pageSize 값 (초기값 1000)
    $scope.pageSizeInput = $scope.pageSize;

    // ▼ Lazy-loading 표시용: 화면에 실제로 렌더링할 개수
    $scope.lazyChunk = 100;           // 한 번 스크롤할 때마다 늘릴 개수
    $scope.maxVisiblePerPage = 1000;  // 한 페이지에서 최대 1000개까지만 보여주고 그 이후는 다음 페이지
    $scope.visibleCount = 0;          // 현재 화면에 표시 중인 개수

    // ▼ 키셋 페이징을 위한 캐싱 구조
    $scope.pagesCache = {};            // 페이지별 데이터 캐시 (1페이지 → 데이터)
    $scope.lastIdForPage = {};         // 각 페이지의 마지막 post_id 저장 (키셋 이동용)

    // ------------------------------------------
    // 페이지 변경 시 화면 맨 위로 스크롤
    // ------------------------------------------
    function scrollToTop() {
        $window.scrollTo(0, 0);
    }

    // ------------------------------------------
    // 현재 페이지에서 보여줄 개수 리셋 (처음 100개만, 최대 1000개까지)
    // ------------------------------------------
    function resetVisibleCount() {
        var maxForThisPage = Math.min(
            $scope.maxVisiblePerPage,
            $scope.pageSize || 0,
            $scope.postList.length || 0
        );

        if (!maxForThisPage) {
            $scope.visibleCount = 0;
        } else {
            $scope.visibleCount = Math.min($scope.lazyChunk, maxForThisPage); // 처음엔 100개만
        }
    }

    // ------------------------------------------
    // pageSize 변경 적용 (입력값 검증 + 캐시 초기화 + 첫 페이지 재조회)
    // ------------------------------------------
    $scope.applyPageSize = function () {
        var size = parseInt($scope.pageSizeInput, 10);

        if (isNaN(size) || size <= 0) {
            alert('1 이상의 숫자를 입력하세요.');
            return;
        }

        // 너무 큰 값 제한 (향후 10000개 로딩중 표시 기능과 연계 예정)
        if (size > 10000) {
            alert('최대 10000까지만 허용합니다.');
            size = 10000;
        }

        $scope.pageSize = size;

        // 캐시/상태 초기화
        $scope.pagesCache = {};
        $scope.lastIdForPage = {};
        $scope.currentPage = 1;

        // 총 페이지 수 다시 계산 (totalItems는 서버에서 이미 받아온 값 사용)
        if ($scope.totalItems && $scope.pageSize) {
            $scope.totalPages = Math.ceil($scope.totalItems / $scope.pageSize);
        } else {
            $scope.totalPages = 0;
        }

        // 첫 페이지 다시 로드
        loadFirstPage();
    };

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
                params: { size: $scope.pageSize },                // pageSize 개수 요청
            })
            .then(function (response) {
                $scope.postList = response.data || [];            // 첫 페이지 데이터 목록 저장
                $scope.currentPage = 1;                           // 현재 페이지 = 1

                $scope.pagesCache[1] = $scope.postList;           // 첫 페이지를 캐시에 저장

                // 현재 페이지 마지막 post_id 저장
                if ($scope.postList.length > 0) {
                    var lastPost = $scope.postList[$scope.postList.length - 1]; // 배열 마지막 요소
                    $scope.lastIdForPage[1] = lastPost.post_id;                 // 첫 페이지 마지막 ID 저장
                }

                // Lazy-loading 표시 개수 리셋 (처음 100개만)
                resetVisibleCount();
                // 첫 페이지 로드시도 맨 위로
                scrollToTop();
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
                resetVisibleCount();                             // 새 페이지에서 다시 100개부터 시작
                scrollToTop();                                   // 이전 페이지로 가도 맨 위로
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
                resetVisibleCount();                             // 새 페이지에서 다시 100개부터 시작
                scrollToTop();                                   // 다음 페이지로 갈 때 맨 위로
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
                        size: $scope.pageSize,                    // pageSize 개수 요청
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

                    // 새 페이지에서도 처음은 100개만 보이도록
                    resetVisibleCount();
                    // 다음 페이지로 갈 때도 맨 위로
                    scrollToTop();
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
    // 다음 페이지 버튼 활성화 여부
    //  - 현재 페이지가 마지막 페이지면 비활성
    //  - 한 페이지에서 최대 보여줄 개수(최대 1000개)를
    //    다 보여주기 전까지는 비활성
    // ------------------------------------------
    $scope.canGoNextPage = function () {
        if ($scope.currentPage >= $scope.totalPages) {
            return false;
        }
        var maxForThisPage = Math.min(
            $scope.maxVisiblePerPage,
            $scope.pageSize || 0,
            $scope.postList.length || 0
        );
        if (!maxForThisPage) {
            return false;
        }
        return $scope.visibleCount >= maxForThisPage;
    };

    // ------------------------------------------
    // 스크롤 이벤트로 Lazy-loading (100개씩 추가)
    // ------------------------------------------
    function handleScroll() {
        if (!$scope.postList || $scope.postList.length === 0) {
            return;
        }

        var maxForThisPage = Math.min(
            $scope.maxVisiblePerPage,
            $scope.pageSize || 0,
            $scope.postList.length || 0
        );

        // 이미 이 페이지에서 보여줄 수 있는 최대치(최대 1000개)를 다 보여줬으면 종료
        if ($scope.visibleCount >= maxForThisPage) {
            return;
        }

        var scrollTop =
            $window.pageYOffset ||
            document.documentElement.scrollTop ||
            document.body.scrollTop ||
            0;
        var windowHeight =
            $window.innerHeight ||
            document.documentElement.clientHeight ||
            document.body.clientHeight ||
            0;
        var docHeight = Math.max(
            document.body.scrollHeight,
            document.documentElement.scrollHeight,
            document.body.offsetHeight,
            document.documentElement.offsetHeight,
            document.body.clientHeight,
            document.documentElement.clientHeight
        );

        // 화면 하단 근처까지 스크롤된 경우 (여유 50px)
        if (scrollTop + windowHeight + 50 >= docHeight) {
            $scope.$applyAsync(function () {
                $scope.visibleCount = Math.min(
                    $scope.visibleCount + $scope.lazyChunk, // 100개 추가
                    maxForThisPage                         // 이 페이지 최대치(최대 1000개)를 넘지 않도록
                );
            });
        }
    }

    angular.element($window).on('scroll', handleScroll);

    $scope.$on('$destroy', function () {
        angular.element($window).off('scroll', handleScroll);
    });

    // ------------------------------------------
    // 화면 표시용 연속 번호 계산 함수
    //  - DB post_id와 상관없이
    //  - totalItems 기준으로 페이지별 번호를 계산
    // ------------------------------------------
    $scope.getRowNumber = function (index) {
        if (!$scope.totalItems || !$scope.pageSize) {
            return '';
        }
        // 예) totalItems=100, currentPage=1, pageSize=10
        //  → index=0 → 100, index=1 → 99 ...
        return $scope.totalItems - (($scope.currentPage - 1) * Number($scope.pageSize)) - index;
    };

    // ------------------------------------------
    // 컨트롤러 초기 실행: 전체 건수 + 첫 페이지 로드
    // ------------------------------------------
    loadTotalInfo();   // 총 개수/전체 페이지 계산
    loadFirstPage();   // 실제 첫 페이지 데이터 로드
});
