/**
 * 3-1. UserListController: 사용자 목록 화면(user-list.html) 제어용
 */
// 'UserListController' 등록. user-list.html과 연결됨.
app.controller('UserListController', function ($scope, $http, $location, $rootScope) {
    // $scope: HTML-컨트롤러 데이터 연결용
    // $http: 서버 통신용
    // $location: 페이지 이동용
    // $rootScope: 전역 데이터 접근용

    // isAdmin 변수: 현재 사용자가 관리자인지 여부 저장 (기본값 false)
    $scope.isAdmin = false;
    // userList 변수: 사용자 목록 담을 빈 배열 생성
    $scope.userList = [];

    // 현재 사용자 역할 감시($watch) 시작. 역할 확인 후 isAdmin 값 설정용.
    // $rootScope.currentUser.role 값의 변화를 감지함.
    const unwatch = $rootScope.$watch('currentUser.role', function(newRole) {
        // newRole 값이 없으면(undefined or null), 아직 로딩 중이므로 대기 (return).
        if (newRole === undefined || newRole === null) return;
        // 일단 역할 값이 확인되면, 더 이상 감시할 필요 없으므로 감시 중단 (unwatch 실행).
        unwatch();

        // 새로 확인된 역할(newRole)이 'ADMIN'이면,
        if (newRole === 'ADMIN') {
            // isAdmin을 true로 설정.
            $scope.isAdmin = true;
            // 관리자일 경우에만 fetchAllUsers 함수 호출하여 사용자 목록 불러옴.
            $scope.fetchAllUsers();
        } else {
            // 'ADMIN'이 아니면 isAdmin을 false로 설정.
            $scope.isAdmin = false;
        }
    }); // 역할 감시($watch) 끝.

    // fetchAllUsers 함수: 서버에서 모든 사용자 목록 가져오는 기능.
    $scope.fetchAllUsers = function () {
        // 서버 '/users' 주소로 GET 요청 보냄.
        $http.get('/users').then(function (response) {
            // 성공 시, 받은 데이터(response.data)를 $scope.userList에 저장 -> HTML 자동 갱신.
            $scope.userList = response.data;
        });
    };

    // deleteUser 함수: 특정 사용자 삭제 기능. HTML 삭제 버튼(ng-click)에서 호출됨.
    $scope.deleteUser = function (userId) {
        // confirm(): 사용자에게 삭제 확인창 띄움. '확인' 시 true 반환.
        if (confirm(userId + ' 사용자를 정말 삭제하시겠습니까?')) {
            // 서버 '/users/{userId}' 주소로 DELETE 요청 보냄.
            $http.delete('/users/' + userId).then(function (response) {
                // 성공 시, fetchAllUsers 함수 호출하여 목록 새로고침.
                $scope.fetchAllUsers();
            });
        }
    };

    // goToEditPage 함수: 사용자 수정 페이지로 이동하는 기능. HTML 수정 버튼(ng-click)에서 호출됨.
    $scope.goToEditPage = function (userId) {
        // $location.path(): 브라우저 주소를 '#!/users/edit/{userId}'로 변경하여 페이지 이동.
        $location.path('/users/edit/' + userId);
    };
    // 컨트롤러 시작 시 fetchAllUsers 함수는 isAdmin이 true일 때만 호출되므로, 여기서는 호출하지 않음.
}); // UserListController 끝.

/**
 * 3-2. UserCreateController: 새 사용자 등록 화면(user-create.html) 제어용.
 */
// 'UserCreateController' 등록. user-create.html과 연결됨.
app.controller('UserCreateController', function ($scope, $http, $location) {
    // newUsers 변수: 여러 명 동시 등록을 위해 사용자 입력 폼 데이터를 담을 배열. 기본 1개 생성.
    // 사용처: user-create.html의 ng-repeat="user in newUsers"
    $scope.newUsers = [{ user_id: '', password: '', name: '', phone: '', email: '' }];

    // addUserField 함수: '+' 버튼(ng-click) 클릭 시, newUsers 배열에 빈 사용자 폼 객체 추가.
    $scope.addUserField = function () {
        $scope.newUsers.push({ user_id: '', password: '', name: '', phone: '', email: '' });
    };

    // removeUserField 함수: '-' 버튼(ng-click) 클릭 시, 해당 인덱스의 사용자 폼 객체 제거 (최소 1개 유지).
    $scope.removeUserField = function (index) {
        if ($scope.newUsers.length > 1) {
            $scope.newUsers.splice(index, 1); // 배열에서 index 위치부터 1개 제거
        }
    };

    // submitUsers 함수: '모두 추가' 버튼(ng-click) 클릭 시 실행.
    $scope.submitUsers = function () {
        // 서버 '/users/bulk' 주소로 newUsers 배열 전체를 POST 전송.
        $http.post('/users/bulk', $scope.newUsers).then(function (res) {
            // res.data: 서버 응답 (각 사용자별 성공 여부 코드 배열 [1, 1, 0] 등)
            // res.data.every(...): 배열 모든 요소가 1이면 true (모두 성공)
            if (res.data.every((code) => code === 1)) {
                alert('모든 사용자가 성공적으로 추가되었습니다!');
            // res.data.some(...): 배열 요소 중 하나라도 1이면 true (일부 성공)
            } else { // (이전 코드에 else if가 있었으나, 실패 조건 통합 가능)
                alert('사용자 추가에 실패했습니다.');
            }
            // 성공/실패 여부와 관계없이 사용자 목록('/users') 페이지로 이동.
            $location.path('/users');
        });
    };
}); // UserCreateController 끝.

/**
 * 3-3. UserEditController: 사용자 수정 화면(user-edit.html) 제어용.
 */
// 'UserEditController' 등록. user-edit.html과 연결됨.
app.controller('UserEditController', function ($scope, $http, $location, $routeParams) {
    // userForm 변수: 수정할 사용자 정보를 담을 빈 객체 생성.
    // 사용처: user-edit.html의 입력창들(ng-model="userForm.name" 등)과 연결됨.
    $scope.userForm = {};
    // $routeParams.userId: 현재 URL에서 ':userId' 부분의 값(수정할 사용자의 ID) 가져오기.
    var userId = $routeParams.userId;

    // 서버 '/users/{userId}' 주소로 GET 요청 보내 사용자 정보 가져오기.
    $http.get('/users/' + userId).then(function (response) {
        // 성공 시, 받은 데이터(response.data)를 $scope.userForm에 저장 -> HTML 입력창 자동 채워짐.
        $scope.userForm = response.data;
    });

    // updateUser 함수: '저장' 버튼(ng-click) 클릭 시 실행.
    $scope.updateUser = function () {
        // 서버 '/users/{user_id}' 주소로 수정된 정보($scope.userForm)를 PUT 전송.
        $http.put('/users/' + $scope.userForm.user_id, $scope.userForm).then(function () {
            // 성공 시, 사용자 목록('/users') 페이지로 이동.
            $location.path('/users');
        });
    };
}); // UserEditController 끝.

/**
 * 3-4. RoleManagementController: 권한 관리 화면(role-management.html) 제어용.
 */
// 'RoleManagementController' 등록. role-management.html과 연결됨.
app.controller('RoleManagementController', function ($scope, $http, $rootScope, $location) {
    // isAdmin 변수: 현재 사용자가 관리자인지 여부 저장 (기본값 false).
    $scope.isAdmin = false;

    // 현재 사용자 역할 감시($watch) 시작. 역할 확인 후 isAdmin 값 설정 및 데이터 로딩용.
    const unwatch = $rootScope.$watch('currentUser.role', function (newRoleValue) {
        // 역할 정보 없으면(undefined or null) 대기 (return).
        if (newRoleValue === undefined || newRoleValue === null) return;
        // 한 번 확인 후 감시 중단.
        unwatch();

        // 역할이 'ADMIN'이면,
        if (newRoleValue === 'ADMIN') {
            // isAdmin을 true로 설정.
            $scope.isAdmin = true;
            // 관리자일 경우에만 initializePageData 함수 호출하여 화면 데이터 불러옴.
            initializePageData();
        } else {
            // 'ADMIN' 아니면 isAdmin을 false로 설정. (HTML에서 관리자 전용 아님 메시지 표시됨)
            $scope.isAdmin = false;
        }
    }); // 역할 감시($watch) 끝.

    // initializePageData 함수: 권한 관리 페이지에 필요한 데이터(사용자 목록, 역할 목록) 로딩 기능.
    function initializePageData() {
        // 변수 초기화.
        $scope.userList = []; // 사용자 목록 담을 배열.
        $scope.roleList = []; // 전체 역할 목록 담을 배열.
        $scope.userRoleSelections = {}; // 각 사용자별 역할 선택 상태(체크박스) 저장용 객체.

        // 1. 서버 '/api/roles' 주소로 GET 요청 보내 전체 역할 목록 가져오기.
        $http.get('/api/roles').then(function (response) {
            // 성공 시, 받은 데이터(response.data)를 $scope.roleList에 저장.
            $scope.roleList = response.data;

            // 2. 역할 목록 로딩 후, 서버 '/users' 주소로 GET 요청 보내 전체 사용자 목록 가져오기.
            $http.get('/users').then(function (response) {
                // 성공 시, 받은 데이터(response.data)를 $scope.userList에 저장.
                $scope.userList = response.data;
                // 각 사용자에 대해 반복(forEach) 실행.
                $scope.userList.forEach(function(user) {
                    // userRoleSelections 객체에 사용자 ID(user.user_id)를 키로 하는 빈 객체 생성.
                    $scope.userRoleSelections[user.user_id] = {};
                    // 사용자가 가진 역할 ID 목록(user.role_ids, 예: "ADMIN,USER")이 있으면,
                    if (user.role_ids) {
                        // 쉼표+공백 기준으로 잘라서 배열(userAssignedRoles) 생성. 예: ["ADMIN", "USER"]
                        const userAssignedRoles = user.role_ids.split(', ');
                        // 전체 역할 목록($scope.roleList)에 대해 반복 실행.
                        $scope.roleList.forEach(function(role) {
                            // 사용자가 가진 역할 배열(userAssignedRoles)에 현재 역할 ID(role.role_id)가 포함(includes)되어 있으면,
                            if (userAssignedRoles.includes(role.role_id)) {
                                // 해당 사용자의 해당 역할 선택 상태를 true로 설정 (체크박스 체크됨).
                                $scope.userRoleSelections[user.user_id][role.role_id] = true;
                            }
                        });
                    }
                }); // 사용자별 반복 끝.
            }); // 사용자 목록 요청 끝.
        }); // 역할 목록 요청 끝.
    } // initializePageData 함수 끝.

    // isRoleAssigned 함수: 특정 사용자에게 특정 역할이 할당되었는지(체크 상태인지) 확인. HTML 체크박스(ng-checked)에서 사용됨.
    $scope.isRoleAssigned = function(user, roleId) {
        // userRoleSelections 객체에 해당 사용자 ID와 역할 ID의 값이 true인지 확인하여 반환. (!!는 boolean으로 변환)
        return !!($scope.userRoleSelections[user.user_id] && $scope.userRoleSelections[user.user_id][roleId]);
    };

    // toggleRoleSelection 함수: 체크박스(ng-click) 클릭 시, 선택 상태(true/false)를 토글(반전).
    $scope.toggleRoleSelection = function(user, roleId) {
        // 해당 사용자의 선택 상태 객체가 없으면 빈 객체 생성 (최초 클릭 시).
        if (!$scope.userRoleSelections[user.user_id]) {
            $scope.userRoleSelections[user.user_id] = {};
        }
        // 현재 선택 상태의 반대 값(!...)으로 변경.
        $scope.userRoleSelections[user.user_id][roleId] = !$scope.userRoleSelections[user.user_id][roleId];
    };

    // saveUserRoles 함수: '변경사항 저장' 버튼(ng-click) 클릭 시 실행.
    $scope.saveUserRoles = function(user) {
        // selectedRoleIds 변수: 현재 체크된 역할들의 ID만 담을 빈 배열 생성.
        const selectedRoleIds = [];
        // angular.forEach: userRoleSelections 객체에서 해당 사용자의 역할 선택 상태들을 반복 확인.
        // isSelected: 현재 역할의 체크 여부(true/false)
        // roleId: 현재 역할의 ID ('ADMIN', 'USER')
        angular.forEach($scope.userRoleSelections[user.user_id], function(isSelected, roleId) {
            // 만약 체크되어 있다면(isSelected가 true),
            if (isSelected) {
                // selectedRoleIds 배열에 해당 역할 ID 추가.
                selectedRoleIds.push(roleId);
            }
        });
        // 저장 확인창 띄움.
        if (confirm(user.name + ' 사용자의 권한을 이대로 저장하시겠습니까?')) {
            // 서버 '/api/users/{user_id}/roles' 주소로 PUT 요청 전송.
            // 요청 본문(body)에는 { roleIds: ['ADMIN', 'USER'] } 형태의 JSON 데이터 전송.
            $http.put('/api/users/' + user.user_id + '/roles', { roleIds: selectedRoleIds })
                .then(function(response) {
                    // 성공 시 알림창.
                    alert('권한이 성공적으로 변경되었습니다.');
                    // 변경된 내용을 화면에 반영하기 위해 데이터 다시 로딩.
                    initializePageData();
                });
        }
    };
}); // RoleManagementController 끝.