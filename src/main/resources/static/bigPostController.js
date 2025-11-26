// 수정됨: 대용량 게시판 목록(pageSize + Lazy-loading) + 등록 + 상세/수정/삭제(trustedHtml 적용)

app.controller('BigPostController', function ($scope, $http, $window, $timeout) {

    $scope.postList = [];              // 현재 화면에 보여줄 게시글 목록
    $scope.currentPage = 1;            // 현재 페이지 번호
    $scope.totalPages = 0;             // 전체 페이지 수
    $scope.totalItems = 0;             // 전체 게시글 수
    $scope.pageSize = 1000;            // 서버에서 한 번에 가져올 개수(기본 1000)

    // 사용자가 입력창에서 조정할 pageSize 값
    $scope.pageSizeInput = $scope.pageSize;

    // Lazy-loading: 화면에 실제로 렌더링할 개수
    $scope.lazyChunk = 100;           // 스크롤마다 +100
    $scope.visibleCount = 0;          // 현재 화면에 보이는 개수

    // 키셋 페이징 캐시
    $scope.pagesCache = {};           // 페이지별 데이터 캐시
    $scope.lastIdForPage = {};        // 각 페이지 마지막 post_id

    // 10000개 모드에서 로딩중 표시용
    $scope.isLoadingPage = false;

    // ------------------------------------------
    // 페이지 변경 시 화면 맨 위로 스크롤
    // ------------------------------------------
    function scrollToTop() {
        $window.scrollTo(0, 0);
    }

    // ------------------------------------------
    // 현재 페이지에서 보여줄 개수 리셋
    //  - 처음에는 100개만
    //  - 최대 pageSize, postList.length 까지
    // ------------------------------------------
    function resetVisibleCount() {
        var maxForThisPage = Math.min(
            $scope.pageSize || 0,
            $scope.postList.length || 0
        );

        if (!maxForThisPage) {
            $scope.visibleCount = 0;
        } else {
            $scope.visibleCount = Math.min($scope.lazyChunk, maxForThisPage);
        }
    }

    // ------------------------------------------
    // pageSize 변경 적용
    // ------------------------------------------
    $scope.applyPageSize = function () {
        var size = parseInt($scope.pageSizeInput, 10);

        if (isNaN(size) || size <= 0) {
            alert('1 이상의 숫자를 입력하세요.');
            return;
        }

        if (size > 10000) {
            alert('최대 10000까지만 허용합니다.');
            size = 10000;
        }

        $scope.pageSize = size;

        // 상태/캐시 초기화
        $scope.pagesCache = {};
        $scope.lastIdForPage = {};
        $scope.currentPage = 1;

        if ($scope.totalItems && $scope.pageSize) {
            $scope.totalPages = Math.ceil($scope.totalItems / $scope.pageSize);
        } else {
            $scope.totalPages = 0;
        }

        loadFirstPage();
    };

    // ------------------------------------------
    // 총 게시글 수 조회
    // ------------------------------------------
    function loadTotalInfo() {
        $http
            .get('/api/big-posts', {
                params: { page: 1, size: 1 },
            })
            .then(function (response) {
                $scope.totalItems = response.data.totalItems || 0;
                if ($scope.pageSize) {
                    $scope.totalPages = Math.ceil($scope.totalItems / $scope.pageSize);
                }
            })
            .catch(function (err) {
                console.error('총 개수 조회 실패:', err);
            });
    }

    // ------------------------------------------
    // 첫 페이지 로드
    // ------------------------------------------
    function loadFirstPage() {
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

                resetVisibleCount();
                scrollToTop();
            })
            .catch(function (err) {
                console.error('첫 페이지 로드 실패:', err);
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
    }

    // ------------------------------------------
    // 페이지 이동 (이전/다음만)
    // ------------------------------------------
    $scope.goToPage = function (page) {
        if (page < 1 || ($scope.totalPages > 0 && page > $scope.totalPages)) {
            return;
        }
        if (page === $scope.currentPage) {
            return;
        }

        // 이전 페이지
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

        // 다음 페이지
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
    //  - 이 페이지에서 pageSize(또는 실제 데이터 수)만큼
    //    전부 Lazy-loading 된 경우에만 true
    // ------------------------------------------
    $scope.canGoNextPage = function () {
        if ($scope.currentPage >= $scope.totalPages) {
            return false;
        }

        var maxForThisPage = Math.min(
            $scope.pageSize || 0,
            $scope.postList.length || 0
        );
        if (!maxForThisPage) {
            return false;
        }

        return $scope.visibleCount >= maxForThisPage;
    };

    // ------------------------------------------
    // 스크롤 Lazy-loading (100개씩 추가)
    // ------------------------------------------
    function handleScroll() {
        if (!$scope.postList || $scope.postList.length === 0) {
            return;
        }

        var maxForThisPage = Math.min(
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

        if (scrollTop + windowHeight + 50 >= docHeight) {

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
                $timeout(function () {
                    $scope.isLoadingPage = false;
                }, 300);
            }
        }
    }

    angular.element($window).on('scroll', handleScroll);

    $scope.$on('$destroy', function () {
        angular.element($window).off('scroll', handleScroll);
    });

    // ------------------------------------------
    // 번호 계산
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


// 대용량 게시판 새 글 작성 컨트롤러
app.controller('BigPostNewController', function ($scope, $http, $location) {
    $scope.post = { title: '', content: '' };

    // 등록
    $scope.submitPost = function () {
        if (!$scope.post.title || !$scope.post.content) {
            alert('제목과 내용을 모두 입력해주세요.');
            return;
        }

        $http
            .post('/api/big-posts', $scope.post)
            .then(function () {
                alert('대용량 게시글이 등록되었습니다.');
                $location.path('/big-posts');
            })
            .catch(function (err) {
                console.error('대용량 게시글 등록 실패:', err);
                alert('등록 중 오류가 발생했습니다.');
            });
    };

    // 취소
    $scope.cancel = function () {
        $location.path('/big-posts');
    };
});


// 대용량 게시판 상세/수정/삭제 컨트롤러
app.controller('BigPostDetailController', function ($scope, $http, $routeParams, $sce, $location) {
    const postId = $routeParams.postId;

    $scope.post = {};
    $scope.editMode = false;
    $scope.editPost = {};

    // HTML 출력용 헬퍼 (big-post-detail.html 의 ng-bind-html 에서 사용)
    $scope.trustedHtml = function (content) {
        if (!content) return '';
        var withBr = String(content).replace(/\n/g, '<br/>');
        return $sce.trustAsHtml(withBr);
    };

    // 상세 조회
    function loadPost() {
        $http
            .get('/api/big-posts/' + postId)
            .then(function (res) {
                $scope.post = res.data || {};
            })
            .catch(function (err) {
                console.error('대용량 게시글 조회 실패:', err);
                alert('게시글을 불러오지 못했습니다.');
                $location.path('/big-posts');
            });
    }

    // 수정 모드 진입
    $scope.startEdit = function () {
        $scope.editMode = true;
        $scope.editPost = {
            title: $scope.post.title,
            content: $scope.post.content,
        };
    };

    // 수정 저장
    $scope.saveEdit = function () {
        if (!$scope.editPost.title || !$scope.editPost.content) {
            alert('제목과 내용을 입력해주세요.');
            return;
        }

        $http
            .put('/api/big-posts/' + postId, $scope.editPost)
            .then(function () {
                alert('게시글이 수정되었습니다.');
                $scope.post.title = $scope.editPost.title;
                $scope.post.content = $scope.editPost.content;
                $scope.editMode = false;
            })
            .catch(function (err) {
                console.error('대용량 게시글 수정 실패:', err);
                alert('수정 중 오류가 발생했습니다.');
            });
    };

    // 수정 취소
    $scope.cancelEdit = function () {
        $scope.editMode = false;
    };

    // 삭제
    $scope.deletePost = function () {
        if (!confirm('게시글을 삭제하시겠습니까?')) {
            return;
        }

        $http
            .delete('/api/big-posts/' + postId)
            .then(function () {
                alert('게시글이 삭제되었습니다.');
                $location.path('/big-posts');
            })
            .catch(function (err) {
                console.error('대용량 게시글 삭제 실패:', err);
                alert('삭제 중 오류가 발생했습니다.');
            });
    };

    // 초기 로드
    loadPost();
});

// 수정됨 끝
