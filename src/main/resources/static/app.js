// 수정됨: /bus 페이지에서 전역 로딩 오버레이 숨기기(currentPath 사용) + 기존 인터셉터 유지

var app = angular.module('busApp', ['ngRoute', 'ngSanitize']);

/**
 * 라우팅 설정
 */
app.config(function ($routeProvider) {
    $routeProvider
        .when('/welcome', {
            templateUrl: 'views/welcome.html',
        })

        .when('/board', {
            templateUrl: 'views/board-list.html',
            controller: 'BoardController',
        })

        .when('/board/new', {
            templateUrl: 'views/board-new.html',
            controller: 'BoardNewController',
        })

        .when('/board/:postId', {
            templateUrl: 'views/board-detail.html',
            controller: 'BoardDetailController',
        })

        .when('/board/edit/:postId', {
            templateUrl: 'views/board-edit.html',
            controller: 'BoardEditController',
        })

        .when('/users', {
            templateUrl: 'views/user-list.html',
            controller: 'UserListController',
        })
        .when('/users/new', {
            templateUrl: 'views/user-create.html',
            controller: 'UserCreateController',
        })
        .when('/users/edit/:userId', {
            templateUrl: 'views/user-edit.html',
            controller: 'UserEditController',
        })
        .when('/roles', {
            templateUrl: 'views/role-management.html',
            controller: 'RoleManagementController',
        })
        .when('/bus', {
            templateUrl: 'views/bus-info.html',
            controller: 'BusController',
        })

        .when('/files/:fileId', {
            templateUrl: 'views/file-view.html',
            controller: 'FileViewController',
        })

        .when('/big-posts', {
            templateUrl: 'views/big-post-list.html',
            controller: 'BigPostController',
        })

        .when('/big-posts/new', {
            templateUrl: 'views/big-post-new.html',
            controller: 'BigPostNewController',
        })

        .when('/big-posts/:postId', {
            templateUrl: 'views/big-post-detail.html',
            controller: 'BigPostDetailController',
        })

        .when('/board-stats', {
            templateUrl: 'views/board-stats.html',
            controller: 'BoardStatsController',
        })

        .when('/big-board-stats', {
            templateUrl: 'views/big-board-stats.html',
            controller: 'BigBoardStatsController',
        })

        .otherwise({
            redirectTo: '/welcome',
        });
});

/**
 * MainController: 헤더, 메뉴, 공통 레이아웃 제어
 */
app.controller('MainController', function ($scope, $http, $location, $rootScope) {
    $scope.$location = $location; // 템플릿에서 현재 경로 체크용
    $rootScope.currentUser = {}; // 전역 사용자 정보
    $rootScope.menuItems = []; // 전역 메뉴

    // 로그인한 사용자 정보 조회
    $http
        .get('/api/me')
        .then(function (response) {
            $rootScope.currentUser = response.data;
        })
        .catch(function (error) {
            $rootScope.currentUser.username = '정보 없음';
            console.error('사용자 정보를 불러오는 데 실패했습니다.', error);
        });

    // 메뉴 정보 조회
    function fetchMenus() {
        $http
            .get('/api/menus')
            .then(function (response) {
                $rootScope.menuItems = response.data;
            })
            .catch(function (error) {
                console.error('메뉴 정보를 불러오는 데 실패했습니다.', error);
            });
    }

    fetchMenus();

    // 회원 탈퇴
    $scope.deleteMyAccount = function () {
        if (confirm('정말 탈퇴하시겠습니까? 모든 정보는 영구적으로 삭제됩니다.')) {
            $http
                .delete('/api/users/me')
                .then(function () {
                    alert('회원 탈퇴가 완료되었습니다.');
                    window.location.href = '/logout';
                })
                .catch(function (error) {
                    alert('회원 탈퇴 중 오류가 발생했습니다.');
                    console.error('Delete account failed:', error);
                });
        }
    };
});

/**
 * 파일 업로드용 디렉티브
 */
app.directive('fileModel', [
    '$parse',
    function ($parse) {
        return {
            restrict: 'A',
            link: function (scope, element, attrs) {
                var modelSetter = $parse(attrs.fileModel).assign;

                element.bind('change', function () {
                    scope.$apply(function () {
                        modelSetter(scope, element[0].files);
                    });
                });
            },
        };
    },
]);

/**
 * 전역 run 블록: 로딩 플래그 + 현재 경로(currentPath) 관리
 */
app.run(function ($rootScope, $location) {
    $rootScope.isLoading = false; // 전체 앱 공용 로딩 플래그
    $rootScope.currentPath = $location.path(); // 현재 라우트 경로 문자열

    // 라우트 변경 시마다 currentPath 갱신
    $rootScope.$on('$routeChangeSuccess', function () {
        $rootScope.currentPath = $location.path();
        // console.log('currentPath =', $rootScope.currentPath);
    });
});

/**
 * 모든 HTTP 요청에 대해 전역 로딩 오버레이를 제어하는 인터셉터
 */
app.config(function ($httpProvider) {
    $httpProvider.interceptors.push(function ($q, $rootScope) {
        let activeRequests = 0;

        function startLoading() {
            activeRequests++;
            $rootScope.isLoading = true;
        }

        function stopLoading() {
            activeRequests--;
            if (activeRequests <= 0) {
                activeRequests = 0;
                $rootScope.isLoading = false;
            }
        }

        return {
            request: function (config) {
                startLoading();
                return config;
            },
            response: function (response) {
                stopLoading();
                return response;
            },
            responseError: function (rejection) {
                stopLoading();
                return $q.reject(rejection);
            },
        };
    });
});

// 수정됨 끝
