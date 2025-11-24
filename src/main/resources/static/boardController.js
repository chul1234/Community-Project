// 'BoardController' (게시판 목록)
app.controller('BoardController', function ($scope, $http, $rootScope) {
    // BoardController 정의 시작 // $rootScope 추가
    //$scope 컨트롤러 와 뷰 연결, $http 백엔드와 HTTP통
    $scope.postList = []; // 게시글 목록 변수 (배열 초기화)

    // [유지] 페이지네이션 상태 변수
    $scope.currentPage = 1; // 현재 페이지 번호 (int, 1부터 시작). 기본값 1

    // ▼▼▼ [수정] '5개씩 보기' 버그 수정 ▼▼▼
    // [] HTML <option value="10">과 일치하도록 '숫자' 10 대 '문자열' "10"으로 변경
    $scope.pageSize = '10'; // 페이지당 보여줄 게시글 수 (String). 기본값 "10"
    // ▲▲▲ [수정] ▲▲▲

    $scope.totalPages = 0; // 총 페이지 수 (int). 백엔드 응답으로 업데이트됨
    $scope.totalItems = 0; // 총 게시글 수 (int). 백엔드 응답으로 업데이트됨

    //페이지 네이션 블록 크기
    $scope.maxPageLinks = 10;

    // [유지] 검색 관련 변수
    // [유지] HTML의 <select ng-model="searchType">과 연결
    $scope.searchType = 'title'; // 기본 검색 기준 'title' (BoardDAO와 일치)
    // [유지] HTML의 <input ng-model="searchKeyword">와 연결
    $scope.searchKeyword = ''; // 기본 검색어 (빈 문자열)

    // [유지] 검색창 표시(Toggle) 여부 변수
    // [유지] $scope.showSearch 변수를 false로 초기화하여 검색창을 기본적으로 숨김
    $scope.showSearch = false;

    /**
     * [수정됨] 특정 페이지의 게시글 목록을 서버에서 불러오는 함수
     * @param {number} page 불러올 페이지 번호
     */
    function fetchPosts(page) {
        // page 파라미터 받음

        // [수정됨] params 객체를 생성하여 page와 size를 담음
        // [] $scope.pageSize가 이제 문자열 "10"이므로, parseInt로 숫자로 변환
        var params = {
            page: page,
            size: parseInt($scope.pageSize, 10), // 10진수 정수로 변환하여 전송
            // [유지] 검색 관련 파라미터 2개 추가
            searchType: $scope.searchType,
            searchKeyword: $scope.searchKeyword,
        };

        // [수정됨] $http.get의 params 옵션으로 params 객체를 전달
        $http
            .get('/api/posts', { params: params }) // page, size, searchType, searchKeyword 파라미터 전송
            .then(function (response) {
                // .then(): 요청 성공 시 콜백 함수 실행. response: 응답 객체
                // response.data: 서버 응답 본문 (BoardServiceImpl에서 반환한 Map 객체)

                // [유지] 응답 데이터 구조에 맞춰 $scope 변수 업데이트
                $scope.postList = response.data.posts; // response.data.posts (게시글 목록 배열) 할당

                // : 게시글별 좋아요 개수 로딩
                $scope.postList.forEach(function (post) {
                    //
                    $scope.loadLikeCountForPost(post); //
                }); //

                $scope.totalPages = response.data.totalPages; // response.data.totalPages (총 페이지 수) 할당
                $scope.totalItems = response.data.totalItems; // response.data.totalItems (총 게시글 수) 할당
                $scope.currentPage = response.data.currentPage; // response.data.currentPage (현재 페이지 번호) 할당
            }); // .then() 끝
    } // fetchPosts 함수 끝

    // [유지] 검색창 열기/닫기 함수
    $scope.openSearch = function () {
        // [유지] showSearch 변수를 true로 변경 (HTML의 ng-show="showSearch" 부분이 표시됨)
        $scope.showSearch = true;
    };
    $scope.closeSearch = function () {
        // [유지] showSearch 변수를 false로 변경 (HTML의 ng-show="!showSearch" 부분이 숨겨짐)
        $scope.showSearch = false;
    };

    /**
     * [유지] HTML의 '검색' 버튼 (ng-click="searchPosts()") 클릭 시 호출됨.
     */
    $scope.searchPosts = function () {
        // searchPosts 함수 정의 시작
        // [유지] 검색은 항상 1페이지부터 결과를 보여줘야 함
        fetchPosts(1);
    }; // searchPosts 함수 정의 끝

    // ▼▼▼ [수정] $watch 삭제, pageSizeChanged에 로직 복원 ▼▼▼
    /**
     * [수정됨] HTML의 select 태그(ng-model="pageSize") 값이 변경될 때(ng-change) 호출됨.
     */
    $scope.pageSizeChanged = function () {
        // [] 페이지 크기가 변경되었으므로, (검색어 유지한 채) 1페이지부터 다시 조회
        fetchPosts(1);
    };

    // [] $watch('pageSize', ...) 함수 삭제
    // ▲▲▲ [수정] ▲▲▲

    /**
     * [수정됨] 특정 페이지로 이동하는 함수. HTML의 페이지 번호/버튼 클릭 시 호출됨 (ng-click)
     * [수정] (주의사항 3) 페이지 이동 시에도 현재 검색어를 유지해야 함.
     * @param {number} pageNumber 이동할 페이지 번호
     */
    $scope.goToPage = function (pageNumber) {
        // goToPage 함수 정의 시작
        // 이동 요청된 pageNumber 유효성 검사 (1 이상, totalPages 이하)
        if (pageNumber >= 1 && pageNumber <= $scope.totalPages) {
            // if 시작
            // [수정] (fetchPosts는 이제 $scope.searchKeyword를 자동으로 포함하여 호출됨)
            fetchPosts(pageNumber); // fetchPosts 함수 호출하여 해당 페이지 데이터 요청
        } // if 끝
    }; // goToPage 함수 끝

    /**
     * [유지] HTML ng-repeat에서 페이지 번호 생성을 위한 헬퍼 함수
     * @param {number} num 생성할 배열의 길이 (totalPages 값 전달됨)
     * @returns {Array} 길이가 num인 빈 배열 ([undefined, undefined, ...])
     */
    $scope.getNumber = function (num) {
        // getNumber 함수 정의 시작
        // new Array(num): JavaScript 내장 함수. 길이가 num인 배열 생성.
        return new Array(num); // 배열 반환
    }; // getNumber 함수 끝

    /**
     * [] 현재 페이지 기준으로 화면에 보여줄 페이지 번호 목록 계산  // 수정됨
     * 예) currentPage=7, totalPages=52, maxPageLinks=10 → [1..10]  // 수정됨
     * currentPage=17 → [11..20] 식으로 동작  // 수정됨
     */
    $scope.getPageRange = function () {
        // 수정됨
        if (!$scope.totalPages || $scope.totalPages < 1) return []; // 수정됨

        var current = $scope.currentPage || 1; // 현재 페이지  // 수정됨
        var blockSize = $scope.maxPageLinks || 10; // 한 블록에 보여줄 개수  // 수정됨

        // 1~10, 11~20, 21~30 ... 단위로 시작/끝 계산  // 수정됨
        var start = Math.floor((current - 1) / blockSize) * blockSize + 1; // 수정됨
        var end = Math.min(start + blockSize - 1, $scope.totalPages); // 수정됨

        var pages = []; // 실제 페이지 번호 배열  // 수정됨
        for (var i = start; i <= end; i++) {
            // 수정됨
            pages.push(i); // 수정됨
        }
        return pages; // 수정됨
    }; // getPageRange 끝  // 수정됨

    // : 게시글 좋아요 개수 조회 함수
    $scope.loadLikeCountForPost = function (post) {
        //
        // 'POST' 타입 게시글에 대한 좋아요 개수 조회
        $http
            .get('/likes/count', {
                params: {
                    type: 'POST', // : 게시글 타입
                    id: post.post_id, // : 게시글 PK
                },
            })
            .then(function (res) {
                //
                post.likeCount = res.data.count; // : 받아온 좋아요 수를 post 객체에 저장
            });
    };

    // : 게시글 좋아요 토글 함수 (목록 화면용)
    $scope.togglePostLike = function (post) {
        //
        // 로그인 여부 확인 (currentUser.user_id 필요)
        if (!$rootScope.currentUser || !$rootScope.currentUser.user_id) {
            //
            alert('로그인이 필요합니다.'); //
            return; //
        }

        // /likes/toggle 호출하여 좋아요 On/Off
        $http
            .post('/likes/toggle', null, {
                //
                params: {
                    type: 'POST', //
                    id: post.post_id, //
                    userId: $rootScope.currentUser.user_id, //
                },
            })
            .then(function (res) {
                //
                // 응답으로 현재 좋아요 상태와 개수 반환됨
                post.liked = res.data.liked; // : true/false
                post.likeCount = res.data.count; // : 총 개수
            });
    };

    // 컨트롤러 로드 시 첫 페이지($scope.currentPage = 1) 게시글 목록을 즉시 불러옴
    // [유지] (이때 $scope.searchKeyword는 ''(빈값)이므로 전체 목록이 조회됨)
    fetchPosts($scope.currentPage); // fetchPosts 함수 초기 호출 (1페이지 로드)
}); // BoardController 정의 끝

// 'BoardNewController' (새 글 작성) -파일 업로드 처리
// ▼▼▼ [수정됨] BoardNewController (Summernote 콜백 및 Logic) ▼▼▼
app.controller('BoardNewController', function ($scope, $http, $location) {
    // BoardNewController 정의 시작
    $scope.post = { title: '', content: '' };

    $scope.uploadFiles = [];
    $scope.uploadFolderFiles = [];

    // 드래그 앤 드롭으로 추가된 파일을 uploadFiles 배열에 넣는 헬퍼 // 추가됨
    function addFilesToUpload(fileList) { // 추가됨
        if (!fileList || !fileList.length) return; // 추가됨

        // FileList를 배열처럼 순회하면서 하나씩 push // 추가됨
        for (var i = 0; i < fileList.length; i++) { // 추가됨
            var f = fileList[i]; // 추가됨
            $scope.uploadFiles.push(f); // 추가됨
        } // 추가됨
    } // 추가됨

    //본문 이미지 input 이 채워줄 FileList
    $scope.inlineImageFilesNew = null;

    // ★★★ [] 본문 이미지 max-width % 값 (기본 100) ★★★
    $scope.inlineImageWidthNew = 100; // 사용자가 %로 입력할 값

    // textarea 커서 위치에 text 삽입하는 헬퍼
    function insertAtCursor(textareaId, text) {
        var textarea = document.getElementById(textareaId);
        if (!textarea) return;

        var start = textarea.selectionStart || 0;
        var end = textarea.selectionEnd || 0;

        var before = textarea.value.substring(0, start);
        var after = textarea.value.substring(end);

        var newValue = before + text + after;
        textarea.value = newValue;

        // ng-model 동기화
        $scope.post.content = newValue;

        // 커서를 삽입된 텍스트 뒤로 이동
        var newPos = (before + text).length;
        textarea.focus();
        textarea.selectionStart = textarea.selectionEnd = newPos;
    }

    //본문 이미지 업로드 + textarea에 <img> 삽입
    $scope.uploadInlineImageForNew = function () {
        if (!$scope.inlineImageFilesNew || $scope.inlineImageFilesNew.length === 0) {
            alert('먼저 이미지를 선택하세요.');
            return;
        }

        var file = $scope.inlineImageFilesNew[0];
        var formData = new FormData();
        formData.append('file', file);

        $http
            .post('/api/editor-images', formData, {
                transformRequest: angular.identity,
                headers: { 'Content-Type': undefined },
            })
            .then(function (response) {
                var data = response.data || {};
                if (data.success && data.url) {
                    // ★★★ [수정됨] 입력한 %값을 이용해 max-width 설정 ★★★
                    var width = parseInt($scope.inlineImageWidthNew, 10);
                    if (isNaN(width) || width <= 0) {
                        width = 100;
                    }
                    if (width > 100) {
                        width = 100;
                    }
                    var styleText = 'max-width:' + width + '%;';

                    var imgTag = '\n<p><img src="' + data.url + '" style="' + styleText + '"></p>\n';

                    // postEditor textarea 커서 위치에 삽입
                    insertAtCursor('postEditor', imgTag);
                } else {
                    alert('이미지 업로드에 실패했습니다.');
                }
            })
            .catch(function (error) {
                console.error('본문 이미지 업로드 실패:', error);
                alert('이미지 업로드 중 오류가 발생했습니다.');
            });
    };

    // [유지] 헬퍼 함수들은 그대로 유지합니다.
    $scope.getAllUploadFiles = function () {
        var list = [];
        var uploadFilesArray = Array.from($scope.uploadFiles || []);
        var uploadFolderFilesArray = Array.from($scope.uploadFolderFiles || []);

        list = list.concat(uploadFilesArray);
        list = list.concat(uploadFolderFilesArray);

        return list;
    };
    $scope.isFolderFile = function (file) {
        return !!(file.webkitRelativePath && file.webkitRelativePath.indexOf('/') !== -1);
    };
    $scope.isImageFile = function (file) {
        return !!(file.type && file.type.indexOf('image') === 0);
    };
    $scope.getDisplayName = function (file) {
        return file.webkitRelativePath && file.webkitRelativePath.length > 0 ? file.webkitRelativePath : file.name;
    };

    // 드래그 앤 드롭 업로드 영역 초기화 함수 // 추가됨
    function initFileDropZone() { // 추가됨
        var dropZone = document.getElementById('fileDropZone'); // 추가됨
        if (!dropZone) { // 추가됨
            return; // 추가됨
        } // 추가됨

        // 기본 브라우저 동작(파일 열기 등) 막기 // 추가됨
        function preventDefaults(e) { // 추가됨
            e.preventDefault(); // 추가됨
            e.stopPropagation(); // 추가됨
        } // 추가됨

        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (eventName) { // 추가됨
            dropZone.addEventListener(eventName, preventDefaults, false); // 추가됨
        }); // 추가됨

        dropZone.addEventListener('dragover', function () { // 추가됨
            dropZone.classList.add('drag-over'); // 추가됨
        }); // 추가됨

        dropZone.addEventListener('dragleave', function () { // 추가됨
            dropZone.classList.remove('drag-over'); // 추가됨
        }); // 추가됨

        dropZone.addEventListener('drop', function (e) { // 추가됨
            dropZone.classList.remove('drag-over'); // 추가됨
            var files = e.dataTransfer && e.dataTransfer.files; // 추가됨
            if (!files || !files.length) { // 추가됨
                return; // 추가됨
            } // 추가됨

            // Angular scope 갱신 // 추가됨
            $scope.$apply(function () { // 추가됨
                addFilesToUpload(files); // 추가됨
            }); // 추가됨
        }); // 추가됨
    } // 추가됨

    // 뷰가 로딩된 후 드롭존 초기화 // 추가됨
    $scope.$on('$viewContentLoaded', function () { // 추가됨
        initFileDropZone(); // 추가됨
    }); // 추가됨

    // 게시글 등록 함수
    $scope.submitPost = function () {
        if ($scope.post.content === '' || $scope.post.content === '<p><br></p>' || !$scope.post.title) {
            alert('제목과 내용을 모두 입력해주세요.');
            return;
        }

        if (confirm('게시글을 등록하시겠습니까?')) {
            var formData = new FormData();

            formData.append('title', $scope.post.title || '');
            formData.append('content', $scope.post.content); //HTML 내용 전송

            // ▼▼▼ [첨부파일] 폴더 업로드 지원 로직 복구 ▼▼▼
            var allFiles = $scope.getAllUploadFiles();
            if (allFiles && allFiles.length > 0) {
                for (var i = 0; i < allFiles.length; i++) {
                    var file = allFiles[i];
                    formData.append('files', file);
                    var path = file.webkitRelativePath || file.name;
                    formData.append('filePaths', path); //filePaths 전송 복구
                }
            }
            // ▲▲▲ [첨부파일] ▲▲▲

            $http
                .post('/api/posts', formData, {
                    transformRequest: angular.identity,
                    headers: { 'Content-Type': undefined },
                })
                .then(function () {
                    alert('게시글이 성공적으로 등록되었습니다.');
                    $location.path('/board');
                })
                .catch(function (error) {
                    alert('게시글 등록에 실패했습니다.');
                    console.error('Post creation failed:', error);
                });
        }
    }; // submitPost 함수 끝

    $scope.cancel = function () {
        $location.path('/board');
    };
}); // BoardNewController 정의 끝

/**
 * ▼▼▼ [수정됨] BoardDetailController (상세보기/댓글/삭제/고정 전용) ▼▼▼
 * (수정 관련 로직이 BoardEditController로 이동됨)
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce, $rootScope, $location) {
    // BoardDetailController 정의 시작
    const postId = $routeParams.postId; // URL에서 postId 파라미터 추출

    // --- 게시글 관련 변수 ---
    $scope.post = {}; // 게시글 데이터 객체
    $scope.canModify = false; // 수정/삭제 권한 여부 (boolean)

    // ▼▼▼ [수정됨] 수정 관련 $scope 변수 삭제 ▼▼▼
    // $scope.isEditing = false; // (삭제)
    // $scope.editData = {}; // (삭제)
    // $scope.newFiles = []; // (삭제)
    // $scope.deletedFileIds = []; // (삭제)
    // ▲▲▲ [수정됨] ▲▲▲

    $scope.fileList = []; // : 상세 화면에서 표시할 첨부파일 목록 // 수정됨

    // --- 댓글 관련 변수 ---
    $scope.comments = []; // 댓글 목록 (배열)
    $scope.newComment = { content: '' }; // 새 댓글 데이터 (객체)

    // [핵심] 권한 확인 함수
    function checkPermissions() {
        // checkPermissions 함수 정의 시작
        if ($scope.post.user_id && $rootScope.currentUser.role) {
            // if 시작
            if ($rootScope.currentUser.role === 'ADMIN' || $scope.post.user_id === $rootScope.currentUser.username) {
                // if 시작 (관리자 또는 작성자)
                $scope.canModify = true; // 수정/삭제 권한 부여 (true 설정)
            } else {
                // else 시작
                $scope.canModify = false; // 권한 없음 (false 설정)
            } // if-else 끝
        } // if 끝
    } // checkPermissions 함수 끝

    // --- 데이터 로드 ---
    function fetchPostDetails() {
        // [유지] 게시글 로드 함수 분리 (고정/해제 후 재호출 위함)
        $http
            .get('/api/posts/' + postId)
            .then(function (response) {
                // .then(): 성공 콜백
                $scope.post = response.data; //성공시 받은 데이터 $scope.post에 저장

                // : 백엔드에서 첨부파일 리스트를 함께 내려주는 경우 처리 (예: response.data.files)
                if (response.data.files) {
                    //
                    $scope.existingFiles = response.data.files; //
                } else {
                    //
                    $scope.existingFiles = []; //
                }

                // : 상세 페이지에서 게시글 좋아요 개수 로딩
                $scope.loadLikeCountForPost($scope.post); //

                checkPermissions(); //게시글 데이터 도착 후, 권한 체크 함수 호출
            })
            .catch(function () {
                // .catch(): 실패 콜백
                alert('게시글을 불러오는데 실패했습니다.'); // 오류 알림
                $location.path('/board'); // 목록 페이지로 이동
            }); // .catch() 끝
    } // fetchPostDetails 함수 끝
    fetchPostDetails(); // 함수 즉시 호출 (초기 로드)

    // : 첨부 파일 목록 조회 함수 // 수정됨
    function fetchFiles() {
        $http
            .get('/api/posts/' + postId + '/files')
            .then(function (response) {
                $scope.fileList = response.data || []; // 수정됨
            })
            .catch(function (error) {
                console.error('파일 목록을 불러오는데 실패했습니다.', error); // 수정됨
                $scope.fileList = []; // 수정됨
            });
    } // 수정됨
    fetchFiles(); // 상세 페이지 최초 진입 시 첨부파일 목록 로드 // 수정됨

    // 댓글 목록 가져오는 함수
    function fetchComments() {
        // fetchComments 함수 정의 시작
        $http.get('/api/posts/' + postId + '/comments').then(function (response) {
            // .then(): 성공 콜백
            $scope.comments = response.data; //성공시 받은 계층형 댓글 목록 $scope.comments에 저장

            // : 댓글/대댓글 전체에 대해 좋아요 개수 로딩
            $scope.applyLikeInfoToComments($scope.comments); //
        }); // .then() 끝
    } // fetchComments 함수 끝
    fetchComments(); // 함수 즉시 호출

    //[핵심] $rootScope.currentUser.role 값이 바뀔 때마다 자동으로 함수를 실행 (AngularJS $watch 기능)
    $rootScope.$watch('currentUser.role', function (newRole) {
        // newRole: 변경된 새 값
        if (newRole) {
            // if 시작
            checkPermissions(); //권한 다시 확인 함수 호출
        } // if 끝
    }); // $watch 끝

    // --- 게시글 관련 함수들 ---

    // ▼▼▼ [수정됨] 수정 관련 함수(switchToEditMode, saveChanges, exitEditMode) 모두 삭제 ▼▼▼
    // (BoardEditController로 이동)
    // ▲▲▲ [수정됨] ▲▲▲

    // 게시글 '삭제' 버튼(HTML ng-click="deletePost()") 클릭 시 실행될 함수
    $scope.deletePost = function () {
        // deletePost 함수 정의 시작
        if (confirm('게시글을 삭제하시겠습니까?')) {
            // window.confirm() 함수
            $http.delete('/api/posts/' + postId).then(function () {
                // 화살표 함수 대 일반 함수로 유지해도 무방
                $location.path('/board'); // 경로 변경
            }); // .then() 끝
        } // if 끝
    }; // deletePost 함수 끝

    // 게시글 내용($scope.post.content)이 변경될 때마다 감시($watch)하여 실행되는 함수
    $scope.$watch('post.content', function (v) {
        if (v) {
            $scope.trustedContent = $sce.trustAsHtml(v.replace(/\n/g, '<br/>'));
        }
    }); // $watch 정의

    // ---  게시글 고정 관련 함수들 (관리자 전용) ---
    /**
     * 게시글 고정 함수. HTML의 '고정하기' 버튼 클릭 시 호출됨 (ng-click="pinPost()")
     * [수정됨] prompt 입력 제거, order 값 1로 고정
     */
    $scope.pinPost = function () {
        // [] 고정 순서(order) 값을 1로 고정 (prompt 제거)
        const order = 1; // order 변수 1 할당

        $http
            .put('/api/posts/' + postId + '/pin', { order: order })
            .then(function () {
                // .then(): 성공 콜백
                alert('게시글이 고정되었습니다.'); // 성공 알림
                fetchPostDetails();
            })
            .catch(function (error) {
                // .catch(): 실패 콜백
                if (error.status === 403) {
                    // 403 Forbidden (권한 문제 또는 개수 제한 문제 - 백엔드에서 구분 어려움 현재)
                    alert('게시글 고정 실패: 권한이 없거나 최대 3개까지만 고정할 수 있습니다.'); // 통합 메시지
                } else {
                    // 그 외 오류
                    alert('게시글 고정 중 오류가 발생했습니다.'); // 일반 오류 알림
                } // if-else 끝
                console.error('Pin post failed:', error); // 콘솔 오류 출력
            }); // .catch() 끝
    }; // pinPost 함수 끝

    /**
     * 게시글 고정 해제 함수. HTML의 '고정 해제' 버튼 클릭 시 호출됨 (ng-click="unpinPost()")
     */
    $scope.unpinPost = function () {
        // unpinPost 함수 정의 시작
        if (confirm('게시글 고정을 해제하시겠습니까?')) {
            // window.confirm() 함수
            $http
                .put('/api/posts/' + postId + '/unpin')
                .then(function () {
                    // .then(): 성공 콜백
                    alert('게시글 고정이 해제되었습니다.'); // 성공 알림
                    fetchPostDetails();
                })
                .catch(function (error) {
                    // .catch(): 실패 콜백
                    if (error.status === 403) {
                        // 403 Forbidden
                        alert('고정 해제 실패: 권한이 없습니다.'); // 권한 없음 알림
                    } else {
                        // 그 외 오류
                        alert('고정 해제 중 오류가 발생했습니다.'); // 일반 오류 알림
                    } // if-else 끝
                    console.error('Unpin post failed:', error); // 콘솔 오류 출력
                }); // .catch() 끝
        } // if 끝
    }; // unpinPost 함수 끝

    // --- [] 게시글 좋아요 관련 함수들 ---

    // 게시글 좋아요 개수 조회
    $scope.loadLikeCountForPost = function (post) {
        //
        $http
            .get('/likes/count', {
                params: {
                    type: 'POST', // 게시글 타입
                    id: postId, // 현재 상세 화면 게시글 ID
                },
            })
            .then(function (res) {
                post.likeCount = res.data.count; // 좋아요 개수 저장
            });
    };

    // 게시글 좋아요 토글 (상세 화면)
    $scope.togglePostLikeDetail = function (post) {
        //
        if (!$rootScope.currentUser || !$rootScope.currentUser.user_id) {
            alert('로그인이 필요합니다.');
            return;
        }

        $http
            .post('/likes/toggle', null, {
                params: {
                    type: 'POST',
                    id: postId,
                    userId: $rootScope.currentUser.user_id,
                },
            })
            .then(function (res) {
                post.liked = res.data.liked; // true / false
                post.likeCount = res.data.count; // 총 개수
            });
    };

    // --- 댓글 관련 함수들 ---

    // : 댓글/대댓글 트리에 좋아요 정보 적용
    $scope.applyLikeInfoToComments = function (commentList) {
        //
        if (!commentList) return; //
        commentList.forEach(function (c) {
            //
            $scope.loadLikeCountForComment(c); //
            if (c.replies && c.replies.length > 0) {
                //  (대댓글 존재 시 재귀 호출)
                $scope.applyLikeInfoToComments(c.replies); //
            }
        });
    };

    // : 댓글/대댓글 좋아요 개수 조회
    $scope.loadLikeCountForComment = function (comment) {
        //
        $http
            .get('/likes/count', {
                params: {
                    type: 'COMMENT', // 댓글/대댓글은 COMMENT 타입으로 통합
                    id: comment.comment_id, // 해당 댓글 PK
                },
            })
            .then(function (res) {
                comment.likeCount = res.data.count; // 좋아요 개수 저장
            });
    };

    // : 댓글/대댓글 좋아요 토글
    $scope.toggleCommentLike = function (comment) {
        //
        if (!$rootScope.currentUser || !$rootScope.currentUser.user_id) {
            // 로그인 체크
            alert('로그인이 필요합니다.');
            return;
        }

        $http
            .post('/likes/toggle', null, {
                params: {
                    type: 'COMMENT', // 댓글/대댓글
                    id: comment.comment_id, // 댓글 ID
                    userId: $rootScope.currentUser.user_id,
                },
            })
            .then(function (res) {
                comment.liked = res.data.liked; // 현재 상태
                comment.likeCount = res.data.count; // 총 개수
            });
    };

    // ▼▼▼ [대댓글 수정] submitComment 함수가 parentId와 commentData를 받도록 수정 ▼▼▼
    /**
     * '댓글 등록' 또는 '답글 등록' 버튼 클릭 시 실행될 함수
     * @param {number|null} parentId - 부모 댓글 ID. 최상위 댓글은 null.
     * @param {object} commentData - 댓글 내용이 담긴 객체. (예: { content: "..." })
     */
    $scope.submitComment = function (parentId, commentData) {
        // submitComment 함수 정의 시작

        // 1. 서버로 전송할 데이터 객체(payload) 생성
        var commentToSend = {
            content: commentData.content, // 댓글 내용
            parent_comment_id: parentId, // 부모 ID (null일 수도 있음)
        };

        // $http.post(url, data): HTTP POST 데이터 전송
        $http
            .post('/api/posts/' + postId + '/comments', commentToSend)
            .then(function () {
                // .then(): 성공 콜백

                // 2. 성공 시 입력창 초기화
                if (parentId === null) {
                    // 최상위 댓글 폼의 내용만 비움
                    $scope.newComment.content = '';
                }

                // 3. fetchComments() 함수 호출하여 댓글 목록 다시 불러와 화면 갱
                fetchComments();
            })
            .catch(function () {
                alert('댓글 등록 실패');
            }); // 실패 시 알림창
    }; // submitComment 함수 끝
    // ▲▲▲ [대댓글 수정] submitComment 함수 수정 완료 ▲▲▲

    // (HTML에서 ng-if="canModifyComment(comment)"로 사용) 댓글 수정/삭제 권한 확인 함수. comment 객체(c) 인자로 받음
    $scope.canModifyComment = function (c) {
        // canModifyComment 함수 정의 시작
        return $rootScope.currentUser.role === 'ADMIN' || c.user_id === $rootScope.currentUser.username; // boolean 반환
    }; // canModifyComment 함수 끝

    // 댓글 '삭제' 버튼(HTML ng-click="deleteComment(comment.comment_id)") 클릭 시 실행될 함수. commentId(cId) 인자로 받음
    $scope.deleteComment = function (cId) {
        // deleteComment 함수 정의 시작
        if (confirm('댓글을 삭제하시겠습니까?')) {
            // window.confirm() 함수
            $http.delete('/api/comments/' + cId).then(function () {
                fetchComments(); // 성공 시 fetchComments() 호출하여 목록 새로고침
            });
        } // if 끝
    }; // deleteComment 함수 끝

    // 댓글 '수정' 버튼(HTML ng-click="switchToCommentEditMode(comment)") 클릭 시 실행될 함수. comment 객체(c) 인자로 받음
    $scope.switchToCommentEditMode = function (c) {
        // switchToCommentEditMode 함수 정의 시작
        c.isEditing = true; // 해당 댓글 객체(c)의 isEditing 속성 true 설정 (수정 폼 보이게 됨)
        c.editContent = c.content; // 원본 내용(c.content)을 수정용 속성(c.editContent)에 복사
    }; // switchToCommentEditMode 함수 끝

    // ▼▼▼ [수정] 함수 이름 변경 (게시물 수정과의 충돌 해결) ▼▼▼
    // 댓글 '저장' 버튼(HTML ng-click="saveCommentChanges(comment)") 클릭 시 실행될 함수.
    $scope.saveCommentChanges = function (c) {
        // [수정] saveCommentChanges 함수 정의 시작
        $http.put('/api/comments/' + c.comment_id, { content: c.editContent }).then(function () {
            // c.comment_id 사용, 성공 콜백
            c.isEditing = false; // 성공 시 isEditing 속성 false 설정 ('보기 모드' 전환)
            fetchComments(); // fetchComments() 호출하여 목록 새로고침
        }); // .then() 끝
    }; // saveCommentChanges 함수 끝
    // ▲▲▲ [수정] 완료 ▲▲▲

    // 댓글 수정 '취소' 버튼(HTML ng-click="cancelCommentEdit(comment)") 클릭 시 실행될 함수. comment 객체(c) 인자로 받음
    $scope.cancelCommentEdit = function (c) {
        c.isEditing = false; // isEditing 속성 false 설정 ('보기 모드' 전환)
    }; // cancelCommentEdit 함수 끝
}); // BoardDetailController 정의 끝

// ▼▼▼ [ 추가] BoardEditController (수정 전용) ▼▼▼
app.controller('BoardEditController', function ($scope, $http, $routeParams, $location) {
    const postId = $routeParams.postId;

    // 1. $scope 변수 초기화
    $scope.post = {}; // 게시글(제목, 내용) 데이터
    $scope.fileList = []; // 기존 첨부 파일 목록
    $scope.newFiles = []; // 새로 추가할 파일 목록
    $scope.deletedFileIds = []; // 삭제할 파일 ID 목록

    // 수정 화면용 본문 이미지 FileList
    $scope.inlineImageFilesEdit = null;

    // ★★★ [] 수정 화면에서도 본문 이미지 max-width % 값 (기본 100) ★★★
    $scope.inlineImageWidthEdit = 100; // 수정 화면용 % 값

    // textarea 커서 위치에 text 삽입 헬퍼 함수
    function insertAtCursor(textareaId, text) {
        var textarea = document.getElementById(textareaId);
        if (!textarea) return;

        var start = textarea.selectionStart || 0;
        var end = textarea.selectionEnd || 0;

        var before = textarea.value.substring(0, start);
        var after = textarea.value.substring(end);

        var newValue = before + text + after;
        textarea.value = newValue;

        // ng-model 동기화
        $scope.post.content = newValue;

        // 커서를 삽입된 텍스트 뒤로 이동
        var newPos = (before + text).length;
        textarea.focus();
        textarea.selectionStart = textarea.selectionEnd = newPos;
    }

    // 본문 이미지 업로드 + textarea에 <img> 삽입 (수정 화면용)
    $scope.uploadInlineImageForEdit = function () {
        if (!$scope.inlineImageFilesEdit || $scope.inlineImageFilesEdit.length === 0) {
            alert('먼저 이미지를 선택하세요.');
            return;
        }

        var file = $scope.inlineImageFilesEdit[0];
        var formData = new FormData();
        formData.append('file', file);

        $http
            .post('/api/editor-images', formData, {
                transformRequest: angular.identity,
                headers: { 'Content-Type': undefined },
            })
            .then(function (response) {
                var data = response.data || {};
                if (data.success && data.url) {
                    // ★★★ [수정됨] 수정 화면에서도 %값으로 max-width 설정 ★★★
                    var width = parseInt($scope.inlineImageWidthEdit, 10);
                    if (isNaN(width) || width <= 0) {
                        width = 100;
                    }
                    if (width > 100) {
                        width = 100;
                    }
                    var styleText = 'max-width:' + width + '%;';

                    var imgTag = '\n<p><img src="' + data.url + '" style="' + styleText + '"></p>\n';

                    // postContent textarea 커서 위치에 삽입
                    insertAtCursor('postContent', imgTag);
                } else {
                    alert('이미지 업로드에 실패했습니다.');
                }
            })
            .catch(function (error) {
                console.error('본문 이미지 업로드 실패:', error);
                alert('이미지 업로드 중 오류가 발생했습니다.');
            });
    };

    // 2. (로딩) 게시글 상세 정보 가져오기 (제목, 내용 채우기)
    $http
    .get('/api/posts/' + postId + '/edit') // ★ 수정됨: 조회수 증가 없는 수정용 API
    .then(function (response) {
        $scope.post = response.data;
        // textarea + ng-model 로 자동 표시되므로 별도 summernote 처리 없음
    })
    .catch(function () {
        alert('게시글 정보를 불러오는데 실패했습니다.');
        $location.path('/board');
    });

    // 3. (로딩) 기존 첨부파일 목록 가져오기
    $http.get('/api/posts/' + postId + '/files').then(function (response) {
        $scope.fileList = response.data || [];
        // 체크박스 초기화
        $scope.fileList.forEach(function (f) {
            f._delete = false;
        });
    });

    // 4. (액션) 수정 완료 버튼 클릭
    $scope.saveChanges = function () {
        if (confirm('수정하시겠습니까?')) {
            // textarea 값과 $scope.post.content 동기화 (안전용)
            var textarea = document.getElementById('postContent');
            if (textarea) {
                $scope.post.content = textarea.value;
            }

            var formData = new FormData();

            // 1) 수정된 제목, 내용
            formData.append('title', $scope.post.title || '');
            formData.append('content', $scope.post.content || '');

            // 2) 삭제 체크된 기존 파일 ID 수집
            $scope.deletedFileIds = [];
            angular.forEach($scope.fileList, function (f) {
                if (f._delete) {
                    $scope.deletedFileIds.push(f.file_id);
                }
            });
            angular.forEach($scope.deletedFileIds, function (id) {
                formData.append('deleteFileIds', id);
            });

            // 3) 새로 추가한 파일들
            if ($scope.newFiles && $scope.newFiles.length > 0) {
                for (var i = 0; i < $scope.newFiles.length; i++) {
                    formData.append('files', $scope.newFiles[i]);
                }
            }

            // 4) PUT 전송
            $http
                .put('/api/posts/' + postId, formData, {
                    transformRequest: angular.identity,
                    headers: { 'Content-Type': undefined },
                })
                .then(function () {
                    alert('게시글이 수정되었습니다.');
                    // 성공 시 상세보기 페이지로 이동
                    $location.path('/board/' + postId);
                })
                .catch(function (error) {
                    alert('게시글 수정 중 오류가 발생했습니다.');
                    console.error('Post update failed:', error);
                });
        }
    }; // saveChanges 끝

    // 5. (액션) 취소 버튼 클릭
    $scope.cancelEdit = function () {
        // 상세보기 페이지로 이동
        $location.path('/board/' + postId);
    };
}); // BoardEditController 정의 끝
// ▲▲▲ [ 추가] ▲▲▲

// ★★★ [공통] file-model 디렉티브 추가 (board-new.html / board-detail.html에서 사용) ★★★
app.directive('fileModel', [
    '$parse',
    function ($parse) {
        //
        return {
            restrict: 'A',
            link: function (scope, element, attrs) {
                var model = $parse(attrs.fileModel);
                var modelSetter = model.assign;

                element.bind('change', function () {
                    scope.$apply(function () {
                        // input[type=file]의 FileList를 그대로 scope 변수에 할당
                        modelSetter(scope, element[0].files);
                    });
                });
            },
        };
    },
]); // fileModel 디렉티브 끝 //

// 파일 다운로드뷰 컨트롤러 //
app.controller('FileViewController', function ($scope, $routeParams, $http, $window) {
    $scope.file = null; // 파일 메타데이터 //
    $scope.isImage = false; // 이미지 여부 //
    $scope.viewUrl = ''; // /api/files/{id}/view //
    $scope.downloadUrl = ''; // /api/files/{id}/download //

    var fileId = $routeParams.fileId; // URL에서 파일 ID 추출 //

    // 파일 메타데이터 조회 //
    $http
        .get('/api/files/' + fileId + '/meta')
        .then(function (response) {
            $scope.file = response.data;

            var contentType = $scope.file.content_type || '';
            $scope.isImage = contentType.indexOf('image') === 0;

            $scope.viewUrl = '/api/files/' + fileId + '/view';
            $scope.downloadUrl = '/api.files/' + fileId + '/download'.replace('/api.files', '/api/files'); // 혹시 오타 대비 //수정 가능
            $scope.downloadUrl = '/api/files/' + fileId + '/download'; //깔끔하게 이 줄로 쓰면 됨
        })
        .catch(function (error) {
            console.error('파일 정보를 불러오는 중 오류:', error);
            alert('파일 정보를 불러오는 중 오류가 발생했습니다.');
        });

    // 뒤로가기 //
    $scope.goBack = function () {
        $window.history.back();
    };
});
