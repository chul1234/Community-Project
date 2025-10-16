/**
 * 3-1. UserListController: 사용자 목록 화면(user-list.html)을 제어합니다.
 */
app.controller('UserListController', function ($scope, $http, $location) {
    $scope.userList = [];

    $scope.fetchAllUsers = function () {
        $http.get('/users').then(function (response) {
            $scope.userList = response.data;
        });
    };

    $scope.deleteUser = function (id) {
        if (confirm(id + '번 사용자를 정말 삭제하시겠습니까?')) {
            $http
                .delete('/users/' + id)
                .then(function (response) {
                    $scope.fetchAllUsers();
                })
                .catch(function (error) {
                    console.error('삭제 실패:', error);
                    alert('사용자 삭제 중 오류가 발생했습니다.');
                });
        }
    };

    $scope.goToEditPage = function (id) {
        $location.path('/users/edit/' + id);
    };

    $scope.fetchAllUsers();
});

/**
 * 3-2. UserCreateController: 새 사용자 등록 화면(user-create.html)을 제어합니다.
 */
app.controller('UserCreateController', function ($scope, $http, $location) {
    $scope.newUsers = [{ user_id: '', password: '', name: '', phone: '', email: '' }];

    $scope.addUserField = function () {
        $scope.newUsers.push({ user_id: '', password: '', name: '', phone: '', email: '' });
    };

    $scope.removeUserField = function (index) {
        if ($scope.newUsers.length > 1) {
            $scope.newUsers.splice(index, 1);
        }
    };

    $scope.submitUsers = function () {
        $http.post('/users/bulk', $scope.newUsers).then(function (res) {
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
 * 3-3. UserEditController: 수정 기능만 담당하는 컨트롤러입니다.
 */
app.controller('UserEditController', function ($scope, $http, $location, $routeParams) {
    $scope.userForm = {};
    var id = $routeParams.id;

    $http.get('/users/' + id).then(function (response) {
        $scope.userForm = response.data;
    });

    $scope.updateUser = function () {
        $http.put('/users/' + $scope.userForm.id, $scope.userForm).then(function () {
            $location.path('/users');
        });
    };
});

/**
 * 3-4. RoleManagementController: 권한 관리 화면(role-management.html)을 제어합니다.
 */
app.controller('RoleManagementController', function ($scope, $http, $rootScope, $location) {
    // 페이지 접근 권한을 확인하는 로직 (기존과 동일)
    const unwatch = $rootScope.$watch('currentUser.role', function (newRoleValue) {
        if (newRoleValue) {
            if (newRoleValue !== 'ADMIN') {
                alert('관리자만 접근할 수 있는 페이지입니다.');
                $location.path('/users');
            } else {
                // 관리자일 경우에만 페이지 데이터 로딩 함수를 호출합니다.
                initializePageData();
            }
            unwatch();
        }
    });

    // 페이지에 필요한 데이터를 서버에서 불러오는 함수
    function initializePageData() {
        $scope.userList = [];
        $scope.roleList = [];
        // [신규] 사용자의 역할 선택 상태를 임시로 저장할 객체입니다.
        // 예: { 1: {1: true, 2: true}, 2: {2: true} } -> 1번 유저는 1,2번 역할, 2번 유저는 2번 역할 선택
        $scope.userRoleSelections = {};

        // 1. 전체 역할 목록을 먼저 가져옵니다. (/api/roles)
        $http.get('/api/roles').then(function (response) {
            $scope.roleList = response.data;

            // 2. 역할 목록을 가져온 후, 전체 사용자 목록을 가져옵니다. (/users)
            $http.get('/users').then(function (response) {
                $scope.userList = response.data;

                // 3. 각 사용자의 현재 역할 정보를 분석하여 userRoleSelections 객체에 체크박스 상태를 미리 저장합니다.
                $scope.userList.forEach(function (user) {
                    $scope.userRoleSelections[user.id] = {}; // 사용자 ID로 초기화
                    if (user.role_name) {
                        // "ADMIN, USER" 같은 문자열을 ["ADMIN", "USER"] 배열로 변환
                        const userAssignedRoles = user.role_name.split(', ');

                        // 전체 역할 목록과 사용자의 역할 목록을 비교하여 체크 상태를 결정
                        $scope.roleList.forEach(function (role) {
                            if (userAssignedRoles.includes(role.role_name)) {
                                $scope.userRoleSelections[user.id][role.role_id] = true;
                            }
                        });
                    }
                });
            });
        });
    }

    // [신규] 특정 유저에게 특정 역할이 할당되었는지 확인하는 함수 (ng-checked에서 사용)
    $scope.isRoleAssigned = function (user, roleId) {
        return !!($scope.userRoleSelections[user.id] && $scope.userRoleSelections[user.id][roleId]);
    };

    // [신규] 체크박스를 클릭할 때마다 선택 상태를 토글(true/false)하는 함수
    $scope.toggleRoleSelection = function (user, roleId) {
        if (!$scope.userRoleSelections[user.id]) {
            $scope.userRoleSelections[user.id] = {};
        }
        $scope.userRoleSelections[user.id][roleId] = !$scope.userRoleSelections[user.id][roleId];
    };

    // [신규] '변경사항 저장' 버튼 클릭 시 백엔드 API를 호출하는 함수
    $scope.saveUserRoles = function (user) {
        // 현재 체크된 역할들의 ID만 추출하여 배열로 만듭니다. (예: [1, 2])
        const selectedRoleIds = [];
        angular.forEach($scope.userRoleSelections[user.id], function (isSelected, roleId) {
            if (isSelected) {
                selectedRoleIds.push(parseInt(roleId));
            }
        });

        if (confirm(user.name + ' 사용자의 권한을 이대로 저장하시겠습니까?')) {
            // 우리가 새로 만든 백엔드 API 주소로 PUT 요청을 보냅니다.
            $http
                .put('/api/users/' + user.id + '/roles', { roleIds: selectedRoleIds })
                .then(function (response) {
                    alert('권한이 성공적으로 변경되었습니다.');
                    // 변경된 내용을 화면에 즉시 반영하기 위해 페이지 데이터를 새로고침합니다.
                    initializePageData();
                })
                .catch(function (error) {
                    alert('권한 변경에 실패했습니다. 서버 로그를 확인해주세요.');
                    console.error('Role update failed:', error);
                });
        }
    };
});
