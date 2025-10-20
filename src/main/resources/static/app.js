/**
 * 1. AngularJS 애플리케이션 모듈을 정의합니다.
 * 'ngRoute' 모듈을 의존성으로 추가하여 라우팅 기능을 활성화합니다.
 */
var app = angular.module('busApp', ['ngRoute']);

/**
 * 2. 애플리케이션의 라우팅 규칙을 설정합니다.
 * URL 경로에 따라 어떤 HTML 템플릿과 컨트롤러를 사용할지 정의합니다.
 */
app.config(function ($routeProvider) {
    $routeProvider
        .when('/board', {
            templateUrl: 'views/board-list.html',
            controller: 'BoardController'
        })

        .when('/board/new', {
            templateUrl: 'views/board-new.html',
            controller: 'BoardNewController'
        })

        .when('/board/:postId', {
            templateUrl: 'views/board-detail.html',
            controller: 'BoardDetailController'
        })

        // .when('/board/edit/:postId', {
        //     templateUrl: 'views/board-edit.html',
        //     controller: 'BoardEditController'
        // })

        .when('/users', {
            templateUrl: 'views/user-list.html',
            controller: 'UserListController',
        })
        .when('/users/new', {
            templateUrl: 'views/user-create.html',
            controller: 'UserCreateController',
        })
        // [수정] URL 파라미터를 :id에서 :userId로 변경하여 의미를 명확히 합니다.
        .when('/users/edit/:userId', {
            templateUrl: 'views/user-edit.html',
            controller: 'UserEditController',
        })
        .when('/roles', {
            templateUrl: 'views/role-management.html',
            controller: 'RoleManagementController'
        })
        .when('/bus', {
            templateUrl: 'views/bus-info.html',
            controller: 'BusController',
        })
        .otherwise({
            redirectTo: '/users',
        });
});

/**
 * 3. MainController: 헤더와 같이 공통 레이아웃을 제어합니다.
 */
app.controller('MainController', function($scope, $http, $location, $rootScope) {
    $scope.$location = $location;
    $rootScope.currentUser = {};
    $http.get('/api/me').then(function(response) {
        $rootScope.currentUser = response.data;
    }).catch(function(error) {
        $rootScope.currentUser.username = '정보 없음';
        console.error('사용자 정보를 불러오는 데 실패했습니다.', error);
    });

    $scope.deleteMyAccount = function() {
        if (confirm("정말 탈퇴하시겠습니까? 모든 정보는 영구적으로 삭제됩니다.")) {
            $http.delete('/api/users/me').then(function() {
                alert("회원 탈퇴가 완료되었습니다.");
                window.location.href = '/logout'; 
            }).catch(function(error) {
                alert("회원 탈퇴 중 오류가 발생했습니다.");
                console.error("Delete account failed:", error);
            });
        }
    };
});