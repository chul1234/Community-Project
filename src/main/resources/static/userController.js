/**
 * 3-1. UserListController: 사용자 목록 화면(user-list.html)을 제어합니다.
 */
app.controller('UserListController', function ($scope, $http, $location, $rootScope) {
    $scope.isAdmin = false; // 사용자가 관리자인지 확인하는 변수
    $scope.userList = [];

    // [수정] 현재 사용자의 역할(role)을 확인하여 isAdmin 값을 설정합니다.
    const unwatch = $rootScope.$watch('currentUser.role', function(newRole) {
        if (newRole === undefined || newRole === null) return; // 역할 정보가 로딩될 때까지 대기
        unwatch(); // 한 번만 확인

        if (newRole === 'ADMIN') {
            $scope.isAdmin = true;
            $scope.fetchAllUsers(); // 관리자일 경우에만 사용자 목록을 불러옵니다.
        } else {
            $scope.isAdmin = false;
        }
    });

    $scope.fetchAllUsers = function () {
        $http.get('/users').then(function (response) {
            $scope.userList = response.data;
        });
    };

    $scope.deleteUser = function (userId) {
        if (confirm(userId + ' 사용자를 정말 삭제하시겠습니까?')) {
            $http.delete('/users/' + userId).then(function (response) {
                $scope.fetchAllUsers();
            });
        }
    };

    $scope.goToEditPage = function (userId) {
        $location.path('/users/edit/' + userId);
    };
});

/**
 * 3-2. UserCreateController: 새 사용자 등록 화면(user-create.html)을 제어합니다.
 */
app.controller('UserCreateController', function ($scope, $http, $location) {
    // 이 컨트롤러는 수정할 필요가 없습니다. (기존과 동일)
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
    // 이 컨트롤러는 수정할 필요가 없습니다. (기존과 동일)
    $scope.userForm = {};
    var userId = $routeParams.userId;

    $http.get('/users/' + userId).then(function (response) {
        $scope.userForm = response.data;
    });

    $scope.updateUser = function () {
        $http.put('/users/' + $scope.userForm.user_id, $scope.userForm).then(function () {
            $location.path('/users');
        });
    };
});

/**
 * 3-4. RoleManagementController: 권한 관리 화면(role-management.html)을 제어합니다.
 */
app.controller('RoleManagementController', function ($scope, $http, $rootScope, $location) {
    $scope.isAdmin = false; // 사용자가 관리자인지 확인하는 변수

    // [수정] 경고창과 페이지 이동 대신 isAdmin 값을 설정하는 로직으로 변경
    const unwatch = $rootScope.$watch('currentUser.role', function (newRoleValue) {
        if (newRoleValue === undefined || newRoleValue === null) return;
        unwatch();

        if (newRoleValue === 'ADMIN') {
            $scope.isAdmin = true;
            initializePageData(); // 관리자일 경우에만 데이터를 불러옵니다.
        } else {
            $scope.isAdmin = false;
        }
    });

    function initializePageData() {
        // ... (이하 모든 함수는 기존 코드와 동일)
        $scope.userList = [];
        $scope.roleList = [];
        $scope.userRoleSelections = {};
        $http.get('/api/roles').then(function (response) {
            $scope.roleList = response.data;
            $http.get('/users').then(function (response) {
                $scope.userList = response.data;
                $scope.userList.forEach(function(user) {
                    $scope.userRoleSelections[user.user_id] = {};
                    if (user.role_ids) {
                        const userAssignedRoles = user.role_ids.split(', ');
                        $scope.roleList.forEach(function(role) {
                            if (userAssignedRoles.includes(role.role_id)) {
                                $scope.userRoleSelections[user.user_id][role.role_id] = true;
                            }
                        });
                    }
                });
            });
        });
    }

    $scope.isRoleAssigned = function(user, roleId) {
        return !!($scope.userRoleSelections[user.user_id] && $scope.userRoleSelections[user.user_id][roleId]);
    };
    $scope.toggleRoleSelection = function(user, roleId) {
        if (!$scope.userRoleSelections[user.user_id]) {
            $scope.userRoleSelections[user.user_id] = {};
        }
        $scope.userRoleSelections[user.user_id][roleId] = !$scope.userRoleSelections[user.user_id][roleId];
    };
    $scope.saveUserRoles = function(user) {
        const selectedRoleIds = [];
        angular.forEach($scope.userRoleSelections[user.user_id], function(isSelected, roleId) {
            if (isSelected) {
                selectedRoleIds.push(roleId);
            }
        });
        if (confirm(user.name + ' 사용자의 권한을 이대로 저장하시겠습니까?')) {
            $http.put('/api/users/' + user.user_id + '/roles', { roleIds: selectedRoleIds })
                .then(function(response) {
                    alert('권한이 성공적으로 변경되었습니다.');
                    initializePageData();
                });
        }
    };
});