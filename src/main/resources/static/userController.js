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

    // [수정] 파라미터를 id 대신 user_id로 받고, API 호출 시 user_id를 사용합니다.
    $scope.deleteUser = function (userId) {
        if (confirm(userId + ' 사용자를 정말 삭제하시겠습니까?')) {
            $http.delete('/users/' + userId).then(function (response) {
                $scope.fetchAllUsers();
            }).catch(function (error) {
                console.error('삭제 실패:', error);
                alert('사용자 삭제 중 오류가 발생했습니다.');
            });
        }
    };

    // [수정] 파라미터를 id 대신 user_id로 받고, URL 경로에 user_id를 사용합니다.
    $scope.goToEditPage = function (userId) {
        $location.path('/users/edit/' + userId);
    };

    $scope.fetchAllUsers();
});

/**
 * 3-2. UserCreateController: 새 사용자 등록 화면(user-create.html)을 제어합니다.
 */
app.controller('UserCreateController', function ($scope, $http, $location) {
    // 이 컨트롤러는 수정할 필요가 없습니다.
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
    // 라우트 파라미터가 이제 user_id를 의미하지만, 변수명은 id를 그대로 사용해도 무방합니다.
    var userId = $routeParams.userId; // app.js에서 :userId로 변경했으므로 $routeParams.userId로 받습니다.

    $http.get('/users/' + userId).then(function (response) {
        $scope.userForm = response.data;
    });

    $scope.updateUser = function () {
        // [수정] API 호출 시 userForm에 담긴 user_id를 사용합니다.
        $http.put('/users/' + $scope.userForm.user_id, $scope.userForm).then(function () {
            $location.path('/users');
        });
    };
});

/**
 * 3-4. RoleManagementController: 권한 관리 화면(role-management.html)을 제어합니다.
 */
app.controller('RoleManagementController', function ($scope, $http, $rootScope, $location) {

    const unwatch = $rootScope.$watch('currentUser.role', function (newRoleValue) {
        if (newRoleValue) {
            if (newRoleValue !== 'ADMIN') {
                alert('관리자만 접근할 수 있는 페이지입니다.');
                $location.path('/users');
            } else {
                initializePageData();
            }
            unwatch();
        }
    });

    function initializePageData() {
        $scope.userList = [];
        $scope.roleList = [];
        $scope.userRoleSelections = {};

        $http.get('/api/roles').then(function (response) {
            $scope.roleList = response.data;
            $http.get('/users').then(function (response) {
                $scope.userList = response.data;
                $scope.userList.forEach(function(user) {
                    $scope.userRoleSelections[user.user_id] = {};
                    if (user.role_name) {
                        // user.role_name은 이제 'ADMIN,USER' 같은 role_id 목록입니다.
                        const userAssignedRoles = user.role_name.split(', ');
                        $scope.roleList.forEach(function(role) {
                            // [수정] 사용자의 역할 ID 목록(userAssignedRoles)에
                            // 전체 역할의 역할 ID(role.role_id)가 포함되어 있는지 비교합니다.
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
                })
                .catch(function(error) {
                    alert('권한 변경에 실패했습니다. 서버 로그를 확인해주세요.');
                    console.error("Role update failed:", error);
                });
        }
    };
});