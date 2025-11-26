// 수정됨: 대용량 게시판 Lazy-loading + 10000개 렌더링 시 로딩중 표시

app.controller('BigPostController', function ($scope, $http, $window, $timeout) {

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

    // ▼ pageSize가 10000일 때 렌더링/데이터 로딩 상태 표시용
    $scope.isLoadingPage = false;      // true이면 "로딩중" 오버레이 표시

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
            // 처음에는 100개만 노출
            $scope.visibleCount = Math.min($scope.lazyChunk, maxForThisPage);
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

        // 요구사항: 최대 10000
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
        $http
            .get('/api/big-posts', {
                params: { page: 1, size: 1 },   // DB 부하 최소화 목적
            })
            .then(function (response) {
                $scope.totalItems = response.data.totalItems;      // 전체 게시글 수 저장
                $scope.totalPages = Math.ceil($scope.totalItems / $scope.pageSize); // 총 페이지 계산
            })
            .catch(function (err) {
                console.error('총 개수 조회 실패:', err);
            });
    }

    // ------------------------------------------
    // 첫 페이지 로드 (키셋 페이징: /api/big-posts/first)
    //  - pageSize=10000이면 데이터 로드 + 렌더링을 고려해 로딩중 표시
    // ------------------------------------------
    function loadFirstPage() {
        // pageSize가 10000일 때는 로딩중 플래그 켜기
        $scope.isLoadingPage = ($scope.pageSize >= 10000);

        $http
            .get('/api/big-posts/first', {
                params: { size: $scope.pageSize },
            })
            .then(function (response) {
                $scope.postList = response.data || [];
                $scope.currentPage = 1;

                $scope.pagesCache[1] = $scope.postList;

                if ($scope.postList.length > 0) {
                    var lastPost = $scope.postList[$scope.postList.length - 1];
                    $scope.lastIdForPage[1] = lastPost.post_id;
                }

                resetVisibleCount();   // 처음은 100개만
                scrollToTop();
            })
            .catch(function (err) {
                console.error('첫 페이지 로드 실패:', err);
            })
            .finally(function () {
                // 네가 원하는 건 "렌더링도 느리다"는 걸 보여주는 거니까
                // HTTP 끝나자마자 바로 끄지 말고, 10000개 모드일 때는
                // 약간의 시간 동안 로딩중을 유지시키는 식으로 처리
                if ($scope.pageSize >= 10000) {
                    $timeout(function () {
                        $scope.isLoadingPage = false;
                    }, 300); // 0.3초 정도 유지 (원하면 조절 가능)
                } else {
                    $scope.isLoadingPage = false;
                }
            });
    }

    // ------------------------------------------
    // 페이지 이동 (키셋 페이징: 이전/다음만 허용)
    // ------------------------------------------
    $scope.goToPage = function (page) {

        if (page < 1 || ($scope.totalPages > 0 && page > $scope.totalPages)) {
            return;
        }

        if (page === $scope.currentPage) {
            return;
        }

        // [이전 페이지]
        if (page === $scope.currentPage - 1) {
            var cachedPrev = $scope.pagesCache[page];
            if (cachedPrev) {
                $scope.postList = cachedPrev;
                $scope.currentPage = page;
                resetVisibleCount();
                scrollToTop();
            }
            return;
        }

        // [다음 페이지]
        if (page === $scope.currentPage + 1) {

            var cachedNext = $scope.pagesCache[page];
            if (cachedNext) {
                $scope.postList = cachedNext;
                $scope.currentPage = page;
                resetVisibleCount();
                scrollToTop();
                return;
            }

            var lastId = $scope.lastIdForPage[$scope.currentPage];
            if (!lastId) {
                return;
            }

            // pageSize=10000인 상태에서 다음 페이지도 마찬가지로 무거우니까 로딩중
            $scope.isLoadingPage = ($scope.pageSize >= 10000);

            $http
                .get('/api/big-posts/next', {
                    params: {
                        lastId: lastId,
                        size: $scope.pageSize,
                    },
                })
                .then(function (response) {
                    var data = response.data || [];

                    if (data.length === 0) {
                        return;
                    }

                    $scope.postList = data;
                    $scope.currentPage = page;

                    $scope.pagesCache[page] = data;

                    var lastPost = data[data.length - 1];
                    $scope.lastIdForPage[page] = lastPost.post_id;

                    resetVisibleCount();
                    scrollToTop();
                })
                .catch(function (err) {
                    console.error('다음 페이지 로드 실패:', err);
                })
                .finally(function () {
                    if ($scope.pageSize >= 10000) {
                        $timeout(function () {
                            $scope.isLoadingPage = false;
                        }, 300);
                    } else {
                        $scope.isLoadingPage = false;
                    }
                });

            return;
        }
    };

    // ------------------------------------------
    // 다음 페이지 버튼 활성화 여부
    //  - Lazy-loading 으로 이 페이지 최대치(최대 1000개)까지
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
    //  - pageSize=10000이면, 여기서도 "렌더링 중" 느낌을 주기 위해
    //    isLoadingPage를 잠깐 true로 켰다가 끄는 방식으로 표시
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

        // 화면 하단 근처까지 스크롤된 경우
        if (scrollTop + windowHeight + 50 >= docHeight) {

            // 10000개 모드일 때만 "렌더링 중" 오버레이를 잠깐 보여줌
            if ($scope.pageSize >= 10000) {
                $scope.$applyAsync(function () {
                    $scope.isLoadingPage = true;
                });
            }

            $scope.$applyAsync(function () {
                $scope.visibleCount = Math.min(
                    $scope.visibleCount + $scope.lazyChunk,
                    maxForThisPage
                );
            });

            if ($scope.pageSize >= 10000) {
                // DOM 렌더링이 실제로 느린 부분까지 정확히는 못 잡지만,
                // 사용자가 체감하도록 약간의 시간 후에 로딩 해제
                $timeout(function () {
                    $scope.isLoadingPage = false;
                }, 300); // 필요하면 이 값 조절 가능
            }
        }
    }

    angular.element($window).on('scroll', handleScroll);

    $scope.$on('$destroy', function () {
        angular.element($window).off('scroll', handleScroll);
    });

    // ------------------------------------------
    // 화면 표시용 연속 번호 계산 함수
    // ------------------------------------------
    $scope.getRowNumber = function (index) {
        if (!$scope.totalItems || !$scope.pageSize) {
            return '';
        }
        return $scope.totalItems - (($scope.currentPage - 1) * Number($scope.pageSize)) - index;
    };

    // ------------------------------------------
    // 초기 실행
    // ------------------------------------------
    loadTotalInfo();
    loadFirstPage();
});

// 새 글 작성 컨트롤러
app.controller('BigPostNewController', function ($scope, $http, $location, $rootScope) {
    $scope.post = { title: '', content: '' };

    $scope.savePost = function () {
        if (!$scope.post.title) {
            alert('제목을 입력하세요.');
            return;
        }

        var userId =
            $rootScope.currentUser && $rootScope.currentUser.user_id
                ? $rootScope.currentUser.user_id
                : 'anonymous';

        var payload = {
            title: $scope.post.title,
            content: $scope.post.content || '',
            user_id: userId,
        };

        $http
            .post('/api/big-posts', payload)
            .then(function (response) {
                var created = response.data;
                if (created && created.post_id) {
                    $location.path('/big-posts/' + created.post_id);
                } else {
                    $location.path('/big-posts');
                }
            })
            .catch(function (error) {
                console.error('대용량 게시글 등록 실패', error);
                alert('등록 중 오류가 발생했습니다.');
            });
    };

    $scope.goBack = function () {
        $location.path('/big-posts');
    };
});

// 상세 + 수정/삭제 컨트롤러
app.controller(
    'BigPostDetailController',
    function ($scope, $http, $routeParams, $location, $window, $sce) {
        var postId = $routeParams.postId;

        $scope.post = {};
        $scope.editMode = false;
        $scope.editPost = {};

        $scope.trustedHtml = function (html) {
            return $sce.trustAsHtml(html || '');
        };

        function loadPost() {
            $http
                .get('/api/big-posts/' + postId)
                .then(function (response) {
                    $scope.post = response.data || {};
                })
                .catch(function (error) {
                    console.error('대용량 게시글 조회 실패', error);
                    alert('게시글을 불러오지 못했습니다.');
                    $location.path('/big-posts');
                });
        }

        $scope.startEdit = function () {
            $scope.editMode = true;
            $scope.editPost = {
                title: $scope.post.title,
                content: $scope.post.content,
            };
        };

        $scope.cancelEdit = function () {
            $scope.editMode = false;
        };

        $scope.saveEdit = function () {
            if (!$scope.editPost.title) {
                alert('제목을 입력하세요.');
                return;
            }

            var payload = {
                title: $scope.editPost.title,
                content: $scope.editPost.content,
            };

            $http
                .put('/api/big-posts/' + postId, payload)
                .then(function (response) {
                    $scope.post = response.data || $scope.post;
                    $scope.editMode = false;
                })
                .catch(function (error) {
                    console.error('대용량 게시글 수정 실패', error);
                    alert('수정 중 오류가 발생했습니다.');
                });
        };

        $scope.deletePost = function () {
            if (!$window.confirm('정말 삭제하시겠습니까?')) {
                return;
            }

            $http
                .delete('/api/big-posts/' + postId)
                .then(function () {
                    alert('삭제되었습니다.');
                    $location.path('/big-posts');
                })
                .catch(function (error) {
                    console.error('대용량 게시글 삭제 실패', error);
                    alert('삭제 중 오류가 발생했습니다.');
                });
        };

        loadPost();
    }
);

// 수정됨 끝
