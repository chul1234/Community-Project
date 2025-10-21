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
    $scope.$location = $location; // 뷰에서 $location 접근 가능하도록 설정
    $rootScope.currentUser = {}; // 전역 사용자 정보 객체 초기화
    
    // 메뉴 목록을 저장할 전역 변수 초기화
    $rootScope.menuItems = []; 

    $http.get('/api/me').then(function(response) { // 현재 로그인한 사용자 정보 요청
        $rootScope.currentUser = response.data; // 응답 데이터를 전역 변수에 저장
    }).catch(function(error) { // 오류 처리
        $rootScope.currentUser.username = '정보 없음'; // 기본값 설정
        console.error('사용자 정보를 불러오는 데 실패했습니다.', error); // 오류 로그 출력
    });

    // 백엔드에서 계층화된 메뉴 목록을 가져오는 함수
    function fetchMenus() {
        $http.get('/api/menus').then(function(response) {
            //
            // 백엔드 MenuServiceImpl에서 가공한 계층 구조의 메뉴 데이터를 menuItems에 저장
            $rootScope.menuItems = response.data; 
        }).catch(function(error) {
            console.error('메뉴 정보를 불러오는 데 실패했습니다.', error);
        });
    }
    
    // 컨트롤러가 로드될 때 메뉴 목록을 즉시 불러옴
    fetchMenus();

    $scope.deleteMyAccount = function() { // 회원 탈퇴 함수
        if (confirm("정말 탈퇴하시겠습니까? 모든 정보는 영구적으로 삭제됩니다.")) { // 사용자 확인
            $http.delete('/api/users/me').then(function() { // HTTP DELETE 요청을 보내 회원 탈퇴를 요청
                alert("회원 탈퇴가 완료되었습니다."); // 완료 알림
                window.location.href = '/logout';  // 로그아웃 및 메인 페이지로 이동
            }).catch(function(error) { // 오류 처리
                alert("회원 탈퇴 중 오류가 발생했습니다."); // 오류 알림
                console.error("Delete account failed:", error);
            });
        }
    };
});