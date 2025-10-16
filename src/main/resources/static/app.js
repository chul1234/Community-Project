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
        .when('/users', {
            templateUrl: 'views/user-list.html',
            controller: 'UserListController',
        })
        .when('/users/new', {
            templateUrl: 'views/user-create.html',
            controller: 'UserCreateController',
        })
        .when('/users/edit/:id', {
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
    // 탭 메뉴의 활성화 상태를 CSS('active' 클래스)와 연동하기 위해 $location을 $scope에 할당합니다.
    $scope.$location = $location;

    // 사용자 정보를 앱 전체(모든 컨트롤러)에서 공유하기 위해 '$rootScope'를 사용합니다.
    $rootScope.currentUser = {};

    // 백엔드 API(/api/me)를 호출하여 현재 로그인한 사용자 정보를 가져옵니다.
    $http.get('/api/me').then(function(response) {
        // 성공 시, $rootScope에 사용자 정보(id, role 등)를 모두 저장합니다.
        $rootScope.currentUser = response.data;
    }).catch(function(error) {
        // API 호출 실패 시 (예: 로그아웃 상태) 기본값을 설정합니다.
        $rootScope.currentUser.username = '정보 없음';
        console.error('사용자 정보를 불러오는 데 실패했습니다.', error);
    });
});