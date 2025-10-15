/**
 * 1. AngularJS 애플리케이션 모듈을 정의합니다.
 * 'ngRoute' 모듈을 의존성으로 추가하여 라우팅 기능을 활성화합니다.
 */
var app = angular.module('busApp', ['ngRoute']); //busAPP이라는 모듈 생성, ngRoute를 추가 라우팅 기능 선언

/**
 * 2. 애플리케이션의 라우팅 규칙을 설정합니다.
 * URL 경로에 따라 어떤 HTML 템플릿과 컨트롤러를 사용할지 정의합니다.
 */
app.config(function ($routeProvider) {
    //config()함수는 애플리케이션이 시작될 때 단 한번 실행, $routeProvider서비스를 주입받아 라우팅 규칙을 설정
    $routeProvider // $routeProvider 객체를 사용해 라우팅 규칙을 정의
        .when('/users', {
            // URL 경로가 '#!/users'일 경우
            templateUrl: 'views/user-list.html', // 이 HTML 파일을
            controller: 'UserListController', // 이 컨트롤러와 함께 <ng-view>에 표시합니다.
        })
        .when('/users/new', {
            // URL 경로가 '#!/users/new'일 경우
            templateUrl: 'views/user-create.html', // 이 HTML 파일을
            controller: 'UserCreateController', // 이 컨트롤러와 함께 <ng-view>에 표시합니다.
        })

        .when('/users/edit/:id', {
            // URL에 ID를 포함시킵니다. (예: #!/users/edit/3)
            templateUrl: 'views/user-edit.html', // 수정 전용 HTML 템플릿
            controller: 'UserEditController', // 수정 전용 컨트롤러
        })

        .otherwise({
            redirectTo: '/users', // 그 외 모든 경로로 접속 시 '/users'로 자동 이동시킵니다.
        });
});