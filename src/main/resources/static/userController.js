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
            $http.delete('/users/' + id).then(function (response) {
                $scope.fetchAllUsers();
            }).catch(function (error) {
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

        $http.get('/users').then(function (response) {
            $scope.userList = response.data;
        });

        $http.get('/api/roles').then(function (response) {
            $scope.roleList = response.data;
        });
    }

    $scope.updateRole = function(user, newRoleId) {
        if (!newRoleId) return;

        if (confirm(user.name + ' 사용자의 권한을 변경하시겠습니까?')) {
            $http.put('/api/users/' + user.id + '/role', { roleId: newRoleId })
                .then(function(response) {
                    alert('권한이 성공적으로 변경되었습니다.');
                    const newRole = $scope.roleList.find(r => r.role_id === newRoleId);
                    if (newRole) {
                        user.role_name = newRole.role_name;
                    }
                })
                .catch(function(error) {
                    alert('권한 변경에 실패했습니다.');
                    console.error("Role update failed:", error);
                });
        }
    };
});