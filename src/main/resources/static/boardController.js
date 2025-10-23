// 'BoardController' (게시판 목록)
app.controller('BoardController', function ($scope, $http) { // BoardController 정의 시작
    //$scope 컨트롤러 와 뷰 연결, $http 백엔드와 HTTP통신
    $scope.postList = []; // 게시글 목록 변수 (배열 초기화)

    // [신규 추가됨] 페이지네이션 상태 변수
    $scope.currentPage = 1; // 현재 페이지 번호 (int, 1부터 시작). 기본값 1
    $scope.pageSize = 10; // 페이지당 보여줄 게시글 수 (int). 기본값 10
    $scope.totalPages = 0; // 총 페이지 수 (int). 백엔드 응답으로 업데이트됨
    $scope.totalItems = 0; // 총 게시글 수 (int). 백엔드 응답으로 업데이트됨

    /**
     * [수정됨] 특정 페이지의 게시글 목록을 서버에서 불러오는 함수
     * @param {number} page 불러올 페이지 번호
     */
    function fetchPosts(page) { // page 파라미터 받음
        // $http.get(url, config): GET 요청 전송. config 객체 사용
        // config.params: URL 쿼리 파라미터 설정 객체 (?page=...&size=...)
        $http.get('/api/posts', { params: { page: page, size: $scope.pageSize } }) // page, size 파라미터 전송
            .then(function(response) { // .then(): 요청 성공 시 콜백 함수 실행. response: 응답 객체
                // response.data: 서버 응답 본문 (BoardServiceImpl에서 반환한 Map 객체)

                // [수정됨] 응답 데이터 구조에 맞춰 $scope 변수 업데이트
                $scope.postList = response.data.posts;         // response.data.posts (게시글 목록 배열) 할당
                $scope.totalPages = response.data.totalPages;   // response.data.totalPages (총 페이지 수) 할당
                $scope.totalItems = response.data.totalItems;   // response.data.totalItems (총 게시글 수) 할당
                $scope.currentPage = response.data.currentPage; // response.data.currentPage (현재 페이지 번호) 할당
            }); // .then() 끝
    } // fetchPosts 함수 끝

    /**
     * [신규 추가됨] 특정 페이지로 이동하는 함수. HTML의 페이지 번호/버튼 클릭 시 호출됨 (ng-click)
     * @param {number} pageNumber 이동할 페이지 번호
     */
    $scope.goToPage = function(pageNumber) { // goToPage 함수 정의 시작
        // 이동 요청된 pageNumber 유효성 검사 (1 이상, totalPages 이하)
        if (pageNumber >= 1 && pageNumber <= $scope.totalPages) { // if 시작
            fetchPosts(pageNumber); // fetchPosts 함수 호출하여 해당 페이지 데이터 요청
        } // if 끝
    }; // goToPage 함수 끝

    /**
     * [신규 추가됨] HTML ng-repeat에서 페이지 번호 생성을 위한 헬퍼 함수
     * @param {number} num 생성할 배열의 길이 (totalPages 값 전달됨)
     * @returns {Array} 길이가 num인 빈 배열 ([undefined, undefined, ...])
     */
    $scope.getNumber = function(num) { // getNumber 함수 정의 시작
        // new Array(num): JavaScript 내장 함수. 길이가 num인 배열 생성.
        // HTML의 ng-repeat="i in getNumber(totalPages)" 에서 사용됨.
        // ng-repeat은 배열 길이에 맞춰 반복하며, $index 변수를 제공 (0부터 시작).
        return new Array(num); // 배열 반환
    } // getNumber 함수 끝

    // 컨트롤러 로드 시 첫 페이지($scope.currentPage = 1) 게시글 목록을 즉시 불러옴
    fetchPosts($scope.currentPage); // fetchPosts 함수 초기 호출 (1페이지 로드)
}); // BoardController 정의 끝

// 'BoardNewController' (새 글 작성) - 변경 없음
app.controller('BoardNewController', function ($scope, $http, $location) { // BoardNewController 정의 시작
    // $scope: 뷰와 컨트롤러 연결, $http: 백엔드와 HTTP 통신, $location: 페이지 이동 제어
    $scope.post = { title: '', content: '' }; // 새 게시글 데이터를 담는 객체 초기화
    // post 객체는 title(제목)과 content(내용) 속성을 가짐
    // 게시글 등록 함수
    $scope.submitPost = function () { // submitPost 함수 정의 시작
        // 사용자에게 게시글 등록 여부 확인 (window.confirm() 함수)
        if (confirm("게시글을 등록하시겠습니까?")) { // if 시작
            //$http.post(url, data): HTTP POST 데이터 전송
            //$scope.post: 객체 JSON 형태로 변환 서버 전송
            $http.post('/api/posts', $scope.post).then(function () { // .then(): 요청 성공 콜백
                // 성공 시 알림 (window.alert() 함수) 및 게시판 목록 페이지로 이동
                alert("게시글이 성공적으로 등록되었습니다."); // 알림창 표시
                $location.path('/board'); // $location.path(): AngularJS 경로 변경
            }).catch(function(error) { // .catch(): 요청 실패 콜백. error: 오류 객체
                alert("게시글 등록에 실패했습니다."); // 오류 알림
                console.error("Post creation failed:", error); // console.error(): 개발자 도구 콘솔 오류 출력
            }); // .catch() 끝
        } // if 끝
    }; // submitPost 함수 끝
}); // BoardNewController 정의 끝

/**
 * 게시글 상세 보기, 수정, 삭제 및 댓글, **[신규] 게시글 고정/해제** 를 모두 처리하는 컨트롤러
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce, $rootScope, $location) { // BoardDetailController 정의 시작
   // $routeParams: URL 파라미터 접근, $sce: 신뢰할 수 있는 콘텐츠 처리, $rootScope: 공유되는 전역 저장 공간(사용자 정보)
    const postId = $routeParams.postId; // URL에서 postId 파라미터 추출 ($routeParams 서비스 사용)
    // --- 게시글 관련 변수 ---
    $scope.post = {}; // 게시글 데이터 객체
    $scope.canModify = false; // 수정/삭제 권한 여부 (boolean)
    $scope.isEditing = false; // 수정 모드 여부 (boolean)
    $scope.editData = {}; // 수정 중인 게시글 데이터 (원본 복사본)
    // --- 댓글 관련 변수 ---
    $scope.comments = [];// 댓글 목록 (배열)
    $scope.newComment = { content: '' }; // 새 댓글 데이터 (객체)

    // [핵심] 권한 확인 함수
    // 게시글 및 댓글 수정/삭제 권한 확인 로직
    function checkPermissions() { // checkPermissions 함수 정의 시작
        // 게시글 정보($scope.post.user_id)와 사용자 정보($rootScope.currentUser.role) 로드 완료 확인
        if ($scope.post.user_id && $rootScope.currentUser.role) { // if 시작
            // 데이터 로딩 순서 문제(타이밍 문제)를 해결하기 위해 두 정보가 모두 있을 때만 권한 체크
            // $rootScope.currentUser.role: 현재 로그인 사용자 역할 ('ADMIN' 등)
            // $scope.post.user_id: 현재 게시글 작성자 ID
            // $rootScope.currentUser.username: 현재 로그인 사용자 ID
            if ($rootScope.currentUser.role === 'ADMIN' || $scope.post.user_id === $rootScope.currentUser.username) { // if 시작 (관리자 또는 작성자)
               // 현재 로그인이 관리자 이거나 게시글 작성자 ID가 현재 사용자 같다면
                $scope.canModify = true; // 수정/삭제 권한 부여 (true 설정)
            } else { // else 시작
                // 권한이 없으면 false로 설정
                $scope.canModify = false; // 권한 없음 (false 설정)
            } // if-else 끝
        } // if 끝
    } // checkPermissions 함수 끝

    // --- 데이터 로드 ---
    //게시글 데이터 서버 가져옴
    // $http.get(url): 주소에 GET 요청 전송
    // 백엔드 BoardController.java의 @GetMapping("/api/posts/{postId}") 메소드 호출
    function fetchPostDetails() { // [수정됨] 게시글 로드 함수 분리 (고정/해제 후 재호출 위함)
        $http.get('/api/posts/' + postId).then(function (response) { // .then(): 성공 콜백
            $scope.post = response.data; //성공시 받은 데이터 $scope.post에 저장
            checkPermissions(); //게시글 데이터 도착 후, 권한 체크 함수 호출
        }).catch(function (error) { // .catch(): 실패 콜백
            alert('게시글을 불러오는데 실패했습니다.'); // 오류 알림
            $location.path('/board'); // 목록 페이지로 이동
        }); // .catch() 끝
    } // fetchPostDetails 함수 끝
    fetchPostDetails(); // 함수 즉시 호출 (초기 로드)

    // 댓글 목록 가져오는 함수
    function fetchComments() { // fetchComments 함수 정의 시작
        // $http.get(url): 주소에 GET 요청 전송
        // 백엔드 CommentController.java의 @GetMapping("/api/posts/{postId}/comments") 메소드 호출
        $http.get('/api/posts/' + postId + '/comments').then(function (response) { // .then(): 성공 콜백
            $scope.comments = response.data; //성공시 받은 댓글 목록 $scope.comments에 저장
        }); // .then() 끝
    } // fetchComments 함수 끝
    fetchComments(); // 함수 즉시 호출

    //[핵심] $rootScope.currentUser.role 값이 바뀔 때마다 자동으로 함수를 실행 (AngularJS $watch 기능)
    // $watch(감시 대상, 리스너 함수): 감시 대상 값이 변경될 때 리스너 함수 실행
    $rootScope.$watch('currentUser.role', function(newRole) { // newRole: 변경된 새 값
        // 사용자 정보($rootScope)가 게시글 정보($scope)보다 늦게 도착하는 '타이밍' 문제를 해결 위함
        // newRole 사용자 정보 도착 확인 (null이나 undefined가 아니면)
        if (newRole) { // if 시작
            checkPermissions(); //권한 다시 확인 함수 호출
        } // if 끝
    }); // $watch 끝

    // --- 게시글 관련 함수들 ---
    // 게시글 '수정' 버튼(HTML ng-click="switchToEditMode()") 클릭 시 실행될 함수
    $scope.switchToEditMode = function() { // switchToEditMode 함수 정의 시작
        $scope.isEditing = true; // $scope.isEditing 상태 true 변경 (HTML에서 ng-if="isEditing" 부분이 보이게 됨)
        // angular.copy(원본): 원본 객체/배열 깊은 복사본 생성. 원본 변경 방지.
        $scope.editData = angular.copy($scope.post); // $scope.post 복사하여 $scope.editData에 저장
    }; // switchToEditMode 함수 끝
    // 게시글 '수정 완료' 버튼(HTML ng-click="saveChanges()") 클릭 시 실행될 함수
    $scope.saveChanges = function() { // saveChanges 함수 정의 시작
        if (confirm("수정하시겠습니까?")) { // window.confirm() 함수
            // $http.put(url, data): HTTP PUT 데이터 전송 (수정 요청)
            // 백엔드 BoardController.java의 @PutMapping("/api/posts/{postId}") 메소드 호출
            $http.put('/api/posts/' + postId, $scope.editData).then(function(res) { // res: 응답 객체
                // 성공 시, 서버로부터 받은 최신 데이터(res.data)로 $scope.post 업데이트
                $scope.post = res.data; // post 객체 업데이트
                // $scope.isEditing 상태 false 변경 ('보기 모드' 전환)
                $scope.isEditing = false; // 수정 모드 해제
            }); // .then() 끝
        } // if 끝
    }; // saveChanges 함수 끝
    // 게시글 수정 '취소' 버튼(HTML ng-click="cancelEdit()") 클릭 시 실행될 함수. '보기 모드' 전환.
    $scope.cancelEdit = function() { $scope.isEditing = false; }; // cancelEdit 함수 정의 (isEditing=false 설정)
    // 게시글 '삭제' 버튼(HTML ng-click="deletePost()") 클릭 시 실행될 함수
    $scope.deletePost = function() { // deletePost 함수 정의 시작
        if (confirm("게시글을 삭제하시겠습니까?")) { // window.confirm() 함수
            // $http.delete(url): HTTP DELETE 요청 전송
            // 백엔드 BoardController.java의 @DeleteMapping("/api/posts/{postId}") 메소드 호출
            $http.delete('/api/posts/' + postId).then(() => { // 화살표 함수 사용 (ES6)
                // 성공 시, $location.path() 사용하여 목록 페이지('/board')로 이동
                $location.path('/board'); // 경로 변경
            }); // .then() 끝
        } // if 끝
    }; // deletePost 함수 끝
    // 게시글 내용($scope.post.content)이 변경될 때마다 감시($watch)하여 실행되는 함수
    // $watch(감시 대상, 리스너 함수)
    // HTML 렌더링 위해 줄바꿈 문자(\n)를 HTML 태그(<br/>)로 변환 (String.prototype.replace() 사용)
    // $sce.trustAsHtml(html문자열): AngularJS에게 이 HTML 문자열이 안전함을 알림 (XSS 방지 해제)
    // 결과를 $scope.trustedContent 변수에 저장
    // 사용처: board-detail.html의 ng-bind-html="trustedContent" 부분에서 사용
    $scope.$watch('post.content', function(v) { if(v) $scope.trustedContent = $sce.trustAsHtml(v.replace(/\n/g, '<br/>')); }); // $watch 정의

    // --- [신규 추가됨] 게시글 고정 관련 함수들 (관리자 전용) ---
    /**
     * 게시글 고정 함수. HTML의 '고정하기' 버튼 클릭 시 호출됨 (ng-click="pinPost()")
     * [수정됨] prompt 입력 제거, order 값 1로 고정
     */
    $scope.pinPost = function() { 
        // [신규] 고정 순서(order) 값을 1로 고정 (prompt 제거)
        const order = 1; // order 변수 1 할당

        // $http.put(url, data): HTTP PUT 데이터 전송
        // 백엔드 BoardController.java의 @PutMapping("/api/posts/{postId}/pin") 메소드 호출
        // data: { order: order } 객체 (JSON 변환됨)
        $http.put('/api/posts/' + postId + '/pin', { order: order }).then(function() { // .then(): 성공 콜백
            alert("게시글이 고정되었습니다."); // 성공 알림
            // fetchPostDetails() 함수 호출하여 변경된 게시글 정보(pinned_order) 다시 로드
            fetchPostDetails();
        }).catch(function(error) { // .catch(): 실패 콜백
            // error.status: HTTP 상태 코드 (백엔드에서 400 또는 403 반환 예상)
            // [수정됨] 400 상태 코드일 때 제한 초과 메시지 표시 (BoardServiceImpl 반환값 boolean 기준)
            if (error.status === 403) { // 403 Forbidden (권한 문제 또는 개수 제한 문제 - 백엔드에서 구분 어려움 현재)
                 alert("게시글 고정 실패: 권한이 없거나 최대 3개까지만 고정할 수 있습니다."); // 통합 메시지
            } else { // 그 외 오류
                 alert("게시글 고정 중 오류가 발생했습니다."); // 일반 오류 알림
            } // if-else 끝
            console.error("Pin post failed:", error); // 콘솔 오류 출력
        }); // .catch() 끝
    }; // pinPost 함수 끝

    /**
     * 게시글 고정 해제 함수. HTML의 '고정 해제' 버튼 클릭 시 호출됨 (ng-click="unpinPost()")
     */
    $scope.unpinPost = function() { // unpinPost 함수 정의 시작
        if (confirm("게시글 고정을 해제하시겠습니까?")) { // window.confirm() 함수
            // $http.put(url): HTTP PUT 요청 전송 (본문 없음)
            // 백엔드 BoardController.java의 @PutMapping("/api/posts/{postId}/unpin") 메소드 호출
            $http.put('/api/posts/' + postId + '/unpin').then(function() { // .then(): 성공 콜백
                alert("게시글 고정이 해제되었습니다."); // 성공 알림
                // fetchPostDetails() 함수 호출하여 변경된 게시글 정보(pinned_order = null) 다시 로드
                fetchPostDetails();
            }).catch(function(error) { // .catch(): 실패 콜백
                 if (error.status === 403) { // 403 Forbidden
                     alert("고정 해제 실패: 권한이 없습니다."); // 권한 없음 알림
                 } else { // 그 외 오류
                     alert("고정 해제 중 오류가 발생했습니다."); // 일반 오류 알림
                 } // if-else 끝
                console.error("Unpin post failed:", error); // 콘솔 오류 출력
            }); // .catch() 끝
        } // if 끝
    }; // unpinPost 함수 끝


    // --- 댓글 관련 함수들 ---
    // '댓글 등록' 버튼(HTML ng-click="submitComment()") 클릭 시 실행될 함수
    $scope.submitComment = function() { // submitComment 함수 정의 시작
        // $http.post(url, data): HTTP POST 데이터 전송
        // 백엔드 CommentController.java의 @PostMapping("/api/posts/{postId}/comments") 메소드 호출
        $http.post('/api/posts/' + postId + '/comments', $scope.newComment).then(function() { // .then(): 성공 콜백
            // 성공 시, 입력창($scope.newComment.content) 비움
            $scope.newComment.content = ''; // content 속성 빈 문자열 할당
            // fetchComments() 함수 호출하여 댓글 목록 다시 불러와 화면 갱신
            fetchComments();
        }).catch(function(err) { alert("댓글 등록 실패"); }); // 실패 시 알림창
    }; // submitComment 함수 끝
    // (HTML에서 ng-if="canModifyComment(comment)"로 사용) 댓글 수정/삭제 권한 확인 함수. comment 객체(c) 인자로 받음
    $scope.canModifyComment = function(c) { // canModifyComment 함수 정의 시작
        // 현재 사용자 역할($rootScope.currentUser.role)이 관리자(ADMIN) 이거나(||) 댓글 작성자 ID(c.user_id)가 현재 사용자 ID($rootScope.currentUser.username)와 같으면 true 반환
        return $rootScope.currentUser.role === 'ADMIN' || c.user_id === $rootScope.currentUser.username; // boolean 반환
    }; // canModifyComment 함수 끝
    // 댓글 '삭제' 버튼(HTML ng-click="deleteComment(comment.comment_id)") 클릭 시 실행될 함수. commentId(cId) 인자로 받음
    $scope.deleteComment = function(cId) { // deleteComment 함수 정의 시작
        if (confirm("댓글을 삭제하시겠습니까?")) { // window.confirm() 함수
            // $http.delete(url): HTTP DELETE 요청 전송
            // 백엔드 CommentController.java의 @DeleteMapping("/api/comments/{commentId}") 메소드 호출
            $http.delete('/api/comments/' + cId).then(() => fetchComments()); // 성공 시 fetchComments() 호출하여 목록 새로고침
        } // if 끝
    }; // deleteComment 함수 끝
    // 댓글 '수정' 버튼(HTML ng-click="switchToCommentEditMode(comment)") 클릭 시 실행될 함수. comment 객체(c) 인자로 받음
    $scope.switchToCommentEditMode = function(c) { // switchToCommentEditMode 함수 정의 시작
        c.isEditing = true; // 해당 댓글 객체(c)의 isEditing 속성 true 설정 (수정 폼 보이게 됨)
        c.editContent = c.content; // 원본 내용(c.content)을 수정용 속성(c.editContent)에 복사
    }; // switchToCommentEditMode 함수 끝
    // 댓글 '저장' 버튼(HTML ng-click="saveCommentChanges(comment)") 클릭 시 실행될 함수. comment 객체(c) 인자로 받음
    $scope.saveCommentChanges = function(c) { // saveCommentChanges 함수 정의 시작
        // $http.put(url, data): HTTP PUT 데이터 전송 (수정 요청). data: 수정 내용 객체 { content: ... }
        // 백엔드 CommentController.java의 @PutMapping("/api/comments/{commentId}") 메소드 호출
        $http.put('/api/comments/' + c.comment_id, { content: c.editContent }).then(() => { // c.comment_id 사용, 성공 콜백
            c.isEditing = false; // 성공 시 isEditing 속성 false 설정 ('보기 모드' 전환)
            fetchComments(); // fetchComments() 호출하여 목록 새로고침
        }); // .then() 끝
    }; // saveCommentChanges 함수 끝
    // 댓글 수정 '취소' 버튼(HTML ng-click="cancelCommentEdit(comment)") 클릭 시 실행될 함수. comment 객체(c) 인자로 받음
    $scope.cancelCommentEdit = function(c) { c.isEditing = false; }; // isEditing 속성 false 설정 ('보기 모드' 전환)
}); // BoardDetailController 정의 끝