/**
 * 3-1. UserListController: 사용자 목록 화면(user-list.html)을 제어합니다.
 * 기능: 사용자 목록 조회, 수정 폼 제어, 수정, 삭제
 */
app.controller('UserListController', function ($scope, $http, $location) {
    $scope.userList = []; // user-list.html에서 사용할 변수 초기화

    // 전체 사용자 목록을 서버에서 불러옵니다.
    // /users api로 get 요청을 보내 사용자 목록 가져온뒤 응답받은 데이털ㄹ $scope.userList배열에 저장
    $scope.fetchAllUsers = function () {
        $http.get('/users').then(function (response) {
            $scope.userList = response.data;
        });
    };

    // '삭제' 버튼 클릭 시, 해당 ID의 사용자를 서버에 삭제 요청합니다.
    $scope.deleteUser = function (id) {
    if (confirm(id + '번 사용자를 정말 삭제하시겠습니까?')) {
        $http.delete('/users/' + id).then(function (response) {
            // 성공적으로 삭제되면 목록을 새로고침합니다.
            $scope.fetchAllUsers();
        })
        // 바로 이어서 .catch() 부분을 추가합니다.
        .catch(function (error) {
            // 삭제 실패 시 실행될 코드입니다.
            console.error('삭제 실패:', error);
            alert('사용자 삭제 중 오류가 발생했습니다.');
        });
    }
};

    /**
     * 기능: '수정' 버튼 클릭 시, 해당 ID의 수정 페이지로 이동시킵니다.
     * @param {number} id - 수정할 사용자의 ID
     */
    $scope.goToEditPage = function (id) {
        $location.path('/users/edit/' + id);
    };

    // 컨트롤러가 로드될 때 사용자 목록을 자동으로 불러옵니다.
    $scope.fetchAllUsers();
});

/**
 * 3-2. UserCreateController: 새 사용자 등록 화면(user-create.html)을 제어합니다.
 * 기능: 다중 사용자 입력 폼 추가/삭제, 서버로 전송
 */
// UserCreateController 정의, user-create.html활성화 ($localtion 서비스는 페이지 이동을 위해 주입)
app.controller('UserCreateController', function ($scope, $http, $location) {
    // 화면에 표시될 입력 폼들의 데이터를 담는 배열. 최소 1개의 폼으로 시작합니다.
    // 다중 사용자 입력을 위해, 입력폼 데이터 담을 배열,
    $scope.newUsers = [{ name: '', phone: '', email: '' }];

    // '+' 버튼 클릭 시, 입력 폼을 한 줄 추가합니다.
    //newUsers 배열에 빈 사용자 객체를 하나 더 추가 입력폼 늘어난다.
    $scope.addUserField = function () {
        $scope.newUsers.push({ name: '', phone: '', email: '' });
    };

    // '-' 버튼 클릭 시, 해당 줄의 입력 폼을 제거합니다.
    // 입력 폼이 2개 이상일때 {}코드 실행, 1개 남았을경우 -버튼은 동작 X
    $scope.removeUserField = function (index) {
        if ($scope.newUsers.length > 1) {
            //splice() 배열 기준의 요소를 삭제하거나 새 요소를 추가하여 배열의 내용 변경,splice(시작 인덱스, 제거할 요소의 수)형태로 사용
            $scope.newUsers.splice(index, 1);
        }
    };

    // '모두 추가' 버튼 클릭 시, 입력된 모든 사용자 정보를 서버로 전송합니다.
    $scope.submitUsers = function () {
        // 여러 사용자 정보가 담긴 배열 자체를 '/users/bulk'라는 새로운 API 주소로 POST 요청합니다.
        $http.post('/users/bulk', $scope.newUsers).then(function (res) {
            // 성공 시, $location 서비스를 이용해 사용자 목록 페이지('#!/users')로 자동 이동합니다.
            if (res.data.every((code) => code === 1)) {
                alert('모든 사용자가 성공적으로 추가되었습니다!');
            } else if (res.data.some((code) => code === 1)) {
                alert('일부 사용자가 추가되었습니다. (일부는 실패)');
            } else {
                alert('사용자 추가에 실패했습니다.');
            }
            $location.path('/users');
        });
    };
});

/**
 * --- UserEditController: 수정 기능만 담당하는 새로운 컨트롤러 ---
 * $routeParams 서비스로 URL에 담긴 파라미터 값을 가져옵니다.
 */
app.controller('UserEditController', function ($scope, $http, $location, $routeParams) {
    $scope.userForm = {};
    var id = $routeParams.id; // URL에서 ID 값(예: 3)을 추출합니다.

    // 해당 ID의 사용자 정보를 서버에서 불러와 폼에 미리 채워 둡니다.
    $http.get('/users/' + id).then(function (response) {
        $scope.userForm = response.data;
    });

    // '저장' 버튼을 누르면 서버에 수정 요청을 보냅니다.
    $scope.updateUser = function () {
        $http.put('/users/' + $scope.userForm.id, $scope.userForm).then(function () {
            $location.path('/users'); // 성공 시 목록 페이지로 이동합니다.
        });
    };
});
