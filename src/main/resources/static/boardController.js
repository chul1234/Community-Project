// 'BoardController' (게시판 목록)
app.controller('BoardController', function ($scope, $http, $rootScope) {
    // BoardController 정의 시작 ($rootScope 추가됨)
    // $scope: 컨트롤러와 뷰 연결, $http: 백엔드와 HTTP 통신
    $scope.postList = []; // 게시글 목록 변수 (배열 초기화)

    // [유지] 페이지네이션 상태 변수
    $scope.currentPage = 1; // 현재 페이지 번호 (int, 1부터 시작). 기본값 1

    // ▼▼▼ '5개씩 보기' 버그 수정 ▼▼▼
    // HTML <option value="10">과 일치하도록 '숫자' 10 대 '문자열' "10"으로 변경
    $scope.pageSize = '10'; // 페이지당 보여줄 게시글 수 (String). 기본값 "10"
    // ▲▲▲ 수정 끝 ▲▲▲

    $scope.totalPages = 0; // 총 페이지 수 (int). 백엔드 응답으로 업데이트됨
    $scope.totalItems = 0; // 총 게시글 수 (int). 백엔드 응답으로 업데이트됨

    // 페이지 네이션 블록 크기 (한 번에 보여줄 페이지 번호 개수)
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
     * 특정 페이지의 게시글 목록을 서버에서 불러오는 함수
     * @param {number} page 불러올 페이지 번호
     */
    function fetchPosts(page) {
        // page 파라미터를 받아 지정한 페이지를 조회

        // page와 size, 검색 조건을 하나의 params 객체로 구성
        // $scope.pageSize가 문자열 "10"이므로, parseInt로 숫자로 변환
        var params = {
            page: page,
            size: parseInt($scope.pageSize, 10), // 10진수 정수로 변환하여 전송
            // [유지] 검색 관련 파라미터 2개 추가
            searchType: $scope.searchType,
            searchKeyword: $scope.searchKeyword,
        };

        // /api/posts로 GET 요청을 보내며, params 객체를 쿼리 파라미터로 전달
        $http
            .get('/api/posts', { params: params }) // page, size, searchType, searchKeyword 파라미터 전송
            .then(function (response) {
                // .then(): 요청 성공 시 콜백 함수 실행. response: 응답 객체
                // response.data: 서버 응답 본문 (BoardServiceImpl에서 반환한 Map 객체)

                // 응답 데이터 구조에 맞춰 $scope 변수 업데이트
                $scope.postList = response.data.posts; // response.data.posts (게시글 목록 배열) 할당

                // 각 게시글별 좋아요 개수 로딩
                $scope.postList.forEach(function (post) {
                    // 게시글마다 좋아요 수를 개별 조회
                    $scope.loadLikeCountForPost(post);
                });

                $scope.totalPages = response.data.totalPages; // 총 페이지 수 할당
                $scope.totalItems = response.data.totalItems; // 총 게시글 수 할당
                $scope.currentPage = response.data.currentPage; // 현재 페이지 번호 할당
            });
    } // fetchPosts 함수 끝

    // [유지] 검색창 열기/닫기 함수
    $scope.openSearch = function () {
        // 검색창을 표시하도록 플래그 true 설정
        $scope.showSearch = true;
    };
    $scope.closeSearch = function () {
        // 검색창을 숨기도록 플래그 false 설정
        $scope.showSearch = false;
    };

    /**
     * [유지] HTML의 '검색' 버튼 (ng-click="searchPosts()") 클릭 시 호출됨.
     * 검색은 항상 1페이지부터 다시 조회한다.
     */
    $scope.searchPosts = function () {
        // 검색 요청 시 1페이지부터 조회
        fetchPosts(1);
    };

    // ▼▼▼ $watch 삭제, pageSizeChanged에 로직 복원 ▼▼▼
    /**
     * HTML의 select 태그(ng-model="pageSize") 값이 변경될 때(ng-change) 호출됨.
     * 페이지 크기가 바뀌면 1페이지부터 다시 조회한다.
     */
    $scope.pageSizeChanged = function () {
        // 페이지 크기가 변경되었으므로, (검색어 유지한 채) 1페이지부터 다시 조회
        fetchPosts(1);
    };
    // ▲▲▲ 수정 끝 ▲▲▲

    /**
     * 특정 페이지로 이동하는 함수.
     * HTML의 페이지 번호/버튼 클릭 시 호출됨 (ng-click="goToPage(n)")
     * 페이지 이동 시에도 현재 검색어를 유지해야 함.
     * @param {number} pageNumber 이동할 페이지 번호
     */
    $scope.goToPage = function (pageNumber) {
        // 이동 요청된 pageNumber 유효성 검사 (1 이상, totalPages 이하)
        if (pageNumber >= 1 && pageNumber <= $scope.totalPages) {
            // 유효한 페이지라면 해당 페이지 게시글 목록 조회
            fetchPosts(pageNumber);
        }
    };

    /**
     * [유지] HTML ng-repeat에서 페이지 번호 생성을 위한 헬퍼 함수
     * @param {number} num 생성할 배열의 길이 (totalPages 값 전달됨)
     * @returns {Array} 길이가 num인 빈 배열 ([undefined, undefined, ...])
     */
    $scope.getNumber = function (num) {
        // new Array(num): 길이가 num인 배열 생성 (값은 undefined)
        return new Array(num);
    };

    /**
     * 현재 페이지 기준으로 화면에 보여줄 페이지 번호 목록 계산
     * 예) currentPage=7, totalPages=52, maxPageLinks=10 → [1..10]
     *     currentPage=17 → [11..20] 식으로 동작
     */
    $scope.getPageRange = function () {
        // totalPages가 없거나 1 미만이면 빈 배열 반환
        if (!$scope.totalPages || $scope.totalPages < 1) return [];

        var current = $scope.currentPage || 1; // 현재 페이지 (기본값 1)
        var blockSize = $scope.maxPageLinks || 10; // 한 블록에 보여줄 최대 페이지 개수

        // 1~10, 11~20, 21~30 ... 단위로 시작/끝 계산
        var start = Math.floor((current - 1) / blockSize) * blockSize + 1;
        var end = Math.min(start + blockSize - 1, $scope.totalPages);

        var pages = []; // 실제 페이지 번호 배열
        for (var i = start; i <= end; i++) {
            pages.push(i);
        }
        return pages;
    }; // getPageRange 끝

    // 게시글 좋아요 개수 조회 함수 (목록 화면용)
    $scope.loadLikeCountForPost = function (post) {
        // 해당 게시글의 좋아요 개수를 서버에서 가져옴
        $http
            .get('/likes/count', {
                params: {
                    type: 'POST', // 게시글 타입
                    id: post.post_id, // 게시글 PK
                },
            })
            .then(function (res) {
                // 받아온 좋아요 수를 post 객체에 저장
                post.likeCount = res.data.count;
            });
    };

    // 게시글 좋아요 토글 함수 (목록 화면용)
    $scope.togglePostLike = function (post) {
        // 로그인 여부 확인 (currentUser.user_id 필요)
        if (!$rootScope.currentUser || !$rootScope.currentUser.user_id) {
            alert('로그인이 필요합니다.');
            return;
        }

        // /likes/toggle 호출하여 좋아요 On/Off
        $http
            .post('/likes/toggle', null, {
                params: {
                    type: 'POST', // 게시글 타입
                    id: post.post_id, // 게시글 PK
                    userId: $rootScope.currentUser.user_id, // 현재 로그인 사용자 ID
                },
            })
            .then(function (res) {
                // 응답으로 현재 좋아요 상태와 개수 반환됨
                post.liked = res.data.liked; // true/false
                post.likeCount = res.data.count; // 총 개수
            });
    };

    // 컨트롤러 로드 시 첫 페이지($scope.currentPage = 1) 게시글 목록을 즉시 불러옴
    // 이때 $scope.searchKeyword는 ''(빈값)이므로 전체 목록이 조회됨
    fetchPosts($scope.currentPage); // 1페이지 로드
}); // BoardController 정의 끝

// 'BoardNewController' (새 글 작성) - 파일 업로드 처리 + Summernote 에디터 적용
app.controller('BoardNewController', function ($scope, $http, $location) {
    // BoardNewController 정의 시작
    $scope.post = { title: '', content: '' }; // 새 게시글 제목/내용 모델

    $scope.uploadFiles = [];        // 일반 파일/이미지 업로드 목록
    $scope.uploadFolderFiles = [];  // 폴더 업로드로 들어온 파일 목록

    // ─────────────────────────────
    // 1. 드래그 앤 드롭으로 추가된 파일을 uploadFiles 배열에 넣는 헬퍼
    // ─────────────────────────────
    function addFilesToUpload(fileList) {
        if (!fileList || !fileList.length) return;

        // FileList를 배열처럼 순회하면서 하나씩 push
        for (var i = 0; i < fileList.length; i++) {
            var f = fileList[i];
            $scope.uploadFiles.push(f);
        }
    }

    // ─────────────────────────────
    // 2. Summernote 에디터 초기화 (board-new.html의 ng-init="initEditor()"에서 호출)
    // ─────────────────────────────
    $scope.initEditor = function () {
        // DOM 렌더링 직후에 실행되도록 약간 지연
        setTimeout(function () {
            var $editor = $('#postEditor');
            if (!$editor.length) {
                return;
            }
            // 중복 초기화 방지
            if ($editor.data('summernote-initialized')) {
                return;
            }

            $editor.summernote({
                height: 400,
                lang: 'ko-KR',
                callbacks: {
                    // 에디터 내용 변경 시 post.content에 반영
                    onChange: function (contents) {
                        $scope.$applyAsync(function () {
                            $scope.post.content = contents;
                        });
                    },
                    // 에디터에서 이미지 업로드가 발생했을 때
                    onImageUpload: function (files) {
                        if (files && files.length > 0) {
                            $scope.uploadEditorImage(files[0]);
                        }
                    },
                },
            });

            // 초기 내용이 있다면 에디터에 채워 넣기
            $editor.summernote('code', $scope.post.content || '');

            $editor.data('summernote-initialized', true);
        }, 0);
    };

    // ─────────────────────────────
    // 3. 에디터 내부 이미지 업로드 → /api/editor-images 사용
    // ─────────────────────────────
    $scope.uploadEditorImage = function (file) {
        if (!file) return;

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
                    // 업로드 성공 시 에디터에 이미지 삽입
                    $('#postEditor').summernote('insertImage', data.url);
                } else {
                    alert('이미지 업로드에 실패했습니다.');
                }
            })
            .catch(function (error) {
                console.error('에디터 이미지 업로드 실패:', error);
                alert('이미지 업로드 중 오류가 발생했습니다.');
            });
    };

    // ─────────────────────────────
    // 4. 업로드 대상 파일 전체를 하나의 리스트로 합치는 함수 (기존 유지)
    // ─────────────────────────────
    $scope.getAllUploadFiles = function () {
        var list = [];
        var uploadFilesArray = Array.from($scope.uploadFiles || []);
        var uploadFolderFilesArray = Array.from($scope.uploadFolderFiles || []);

        list = list.concat(uploadFilesArray);
        list = list.concat(uploadFolderFilesArray);

        return list;
    };

    // 폴더 업로드로 들어온 파일인지 여부 확인
    $scope.isFolderFile = function (file) {
        return !!(file.webkitRelativePath && file.webkitRelativePath.indexOf('/') !== -1);
    };

    // 이미지 파일인지 여부 확인
    $scope.isImageFile = function (file) {
        return !!(file.type && file.type.indexOf('image') === 0);
    };

    // 화면에 보여줄 파일 이름 결정 (폴더 경로 또는 파일 이름)
    $scope.getDisplayName = function (file) {
        return file.webkitRelativePath && file.webkitRelativePath.length > 0
            ? file.webkitRelativePath
            : file.name;
    };

    // ─────────────────────────────
    // 5. 드래그 앤 드롭 업로드 영역 초기화 함수 (기존 유지)
    // ─────────────────────────────
    function initFileDropZone() {
        var dropZone = document.getElementById('fileDropZone');
        if (!dropZone) {
            // 드롭존 요소가 없으면 아무 작업도 하지 않음
            return;
        }

        // 기본 브라우저 동작(파일 열기 등) 막기
        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }

        // dragenter, dragover, dragleave, drop 이벤트에서 기본 동작 차단
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (eventName) {
            dropZone.addEventListener(eventName, preventDefaults, false);
        });

        // 드래그 중일 때 스타일 변경
        dropZone.addEventListener('dragover', function () {
            dropZone.classList.add('drag-over');
        });

        // 드래그가 영역을 벗어날 때 스타일 복원
        dropZone.addEventListener('dragleave', function () {
            dropZone.classList.remove('drag-over');
        });

        // 파일이 드롭되었을 때 처리
        dropZone.addEventListener('drop', function (e) {
            dropZone.classList.remove('drag-over');
            var files = e.dataTransfer && e.dataTransfer.files;
            if (!files || !files.length) {
                return;
            }

            // Angular scope 갱신
            $scope.$apply(function () {
                addFilesToUpload(files);
            });
        });
    }

    // 뷰가 로딩된 후 드롭존 초기화
    $scope.$on('$viewContentLoaded', function () {
        initFileDropZone();
    });

    // ─────────────────────────────
    // 6. 게시글 등록 함수 (Summernote 내용 사용하도록 수정)
    // ─────────────────────────────
    $scope.submitPost = function () {
        // 에디터에서 최신 HTML을 가져와서 post.content에 반영
        var editorHtml;
        if (typeof $('#postEditor').summernote === 'function') {
            editorHtml = $('#postEditor').summernote('code');
        } else {
            editorHtml = $scope.post.content || '';
        }
        $scope.post.content = editorHtml;

        // 제목 또는 내용이 비어 있으면 경고
        if (
            !$scope.post.title ||
            !editorHtml ||
            editorHtml === '<p><br></p>'
        ) {
            alert('제목과 내용을 모두 입력해주세요.');
            return;
        }

        if (confirm('게시글을 등록하시겠습니까?')) {
            var formData = new FormData();

            // 제목과 내용 추가
            formData.append('title', $scope.post.title || '');
            formData.append('content', $scope.post.content); // HTML 내용 전송

            // 첨부파일(단일 파일 + 폴더 업로드 파일 포함) 추가
            var allFiles = $scope.getAllUploadFiles();
            if (allFiles && allFiles.length > 0) {
                for (var i = 0; i < allFiles.length; i++) {
                    var file = allFiles[i];
                    formData.append('files', file);
                    var path = file.webkitRelativePath || file.name;
                    // 파일의 상대 경로 또는 파일 이름 전송
                    formData.append('filePaths', path);
                }
            }

            // /api/posts 로 게시글 등록 요청
            $http
                .post('/api/posts', formData, {
                    transformRequest: angular.identity,
                    headers: { 'Content-Type': undefined },
                })
                .then(function () {
                    alert('게시글이 성공적으로 등록되었습니다.');
                    // 등록 후 목록 화면으로 이동
                    $location.path('/board');
                })
                .catch(function (error) {
                    alert('게시글 등록에 실패했습니다.');
                    console.error('Post creation failed:', error);
                });
        }
    }; // submitPost 함수 끝

    // 등록 취소 버튼 클릭 시 목록으로 이동
    $scope.cancel = function () {
        $location.path('/board');
    };
}); // BoardNewController 정의 끝

/**
 * BoardDetailController (상세보기/댓글/삭제/고정 전용)
 * (수정 관련 로직은 BoardEditController로 이동)
 * 게시글 상세 조회, 첨부파일 목록, 댓글, 좋아요, 고정/해제 등을 담당
 */
app.controller('BoardDetailController', function ($scope, $http, $routeParams, $sce, $rootScope, $location) {
    // BoardDetailController 정의 시작
    const postId = $routeParams.postId; // URL에서 postId 파라미터 추출

    // --- 게시글 관련 변수 ---
    $scope.post = {}; // 게시글 데이터 객체
    $scope.canModify = false; // 수정/삭제 권한 여부 (boolean)

    $scope.fileList = []; // 상세 화면에서 표시할 첨부파일 목록

    // --- 댓글 관련 변수 ---
    $scope.comments = []; // 댓글 목록 (배열)
    $scope.newComment = { content: '' }; // 새 댓글 데이터 (객체)

    // 권한 확인 함수 (작성자 또는 관리자 여부 확인)
    function checkPermissions() {
        // 게시글 정보와 로그인 사용자 정보가 있을 때만 체크
        if ($scope.post.user_id && $rootScope.currentUser && $rootScope.currentUser.role) {
            // 관리자 또는 작성자인 경우에만 수정/삭제 가능
            if ($rootScope.currentUser.role === 'ADMIN' || $scope.post.user_id === $rootScope.currentUser.username) {
                $scope.canModify = true; // 수정/삭제 권한 부여
            } else {
                $scope.canModify = false; // 권한 없음
            }
        }
    }

    // --- 데이터 로드 ---

    // 게시글 상세 정보 로드 함수 (수정에서 돌아온 경우 조회수 증가 방지)
    function fetchPostDetails() {
        // 쿼리 스트링에서 fromEdit 값 확인 (예: #!/board/1?fromEdit=true)
        var fromEdit = $location.search().fromEdit === 'true';

        // 수정 화면에서 돌아온 경우 → 조회수 올리지 않는 /edit API 사용
        var url = fromEdit
            ? '/api/posts/' + postId + '/edit'
            : '/api/posts/' + postId;

        $http
            .get(url)
            .then(function (response) {
                $scope.post = response.data; // 게시글 데이터 저장

                // 백엔드에서 첨부파일 리스트를 함께 내려주는 경우 처리 (예: response.data.files)
                if (response.data.files) {
                    $scope.existingFiles = response.data.files;
                } else {
                    $scope.existingFiles = [];
                }

                // 상세 페이지에서 게시글 좋아요 개수 로딩
                $scope.loadLikeCountForPost($scope.post);

                // 게시글 데이터가 로드된 후 권한 체크
                checkPermissions();

                // fromEdit=true는 한 번만 쓰고 제거 → 이후 새로 진입하면 정상적으로 조회수 +1
                if (fromEdit) {
                    $location.search('fromEdit', null);
                }
            })
            .catch(function () {
                alert('게시글을 불러오는데 실패했습니다.');
                // 실패 시 목록 페이지로 이동
                $location.path('/board');
            });
    }
    // 컨트롤러 초기 진입 시 상세 정보 로드
    fetchPostDetails();

    // 첨부 파일 목록 조회 함수
    function fetchFiles() {
        $http
            .get('/api/posts/' + postId + '/files')
            .then(function (response) {
                // 응답이 없을 경우 빈 배열 처리
                $scope.fileList = response.data || [];
            })
            .catch(function (error) {
                console.error('파일 목록을 불러오는데 실패했습니다.', error);
                $scope.fileList = [];
            });
    }
    // 상세 페이지 최초 진입 시 첨부파일 목록 로드
    fetchFiles();

    // 댓글 목록 가져오는 함수
    function fetchComments() {
        $http.get('/api/posts/' + postId + '/comments').then(function (response) {
            // 성공 시 계층형 댓글 목록을 저장
            $scope.comments = response.data;

            // 댓글/대댓글 전체에 대해 좋아요 개수 로딩
            $scope.applyLikeInfoToComments($scope.comments);
        });
    }
    // 컨트롤러 초기 진입 시 댓글 목록 로드
    fetchComments();

    // 로그인 사용자의 role 값이 바뀔 때마다 권한 재확인 (예: 로그인/로그아웃 시)
    $rootScope.$watch('currentUser.role', function (newRole) {
        if (newRole) {
            checkPermissions();
        }
    });

    // --- 게시글 관련 함수들 ---

    // 게시글 '삭제' 버튼 클릭 시 실행될 함수
    $scope.deletePost = function () {
        if (confirm('게시글을 삭제하시겠습니까?')) {
            $http.delete('/api/posts/' + postId).then(function () {
                // 삭제 후 목록 페이지로 이동
                $location.path('/board');
            });
        }
    };

    // 게시글 내용이 변경될 때마다 감시하여 HTML로 신뢰 표시
    $scope.$watch('post.content', function (v) {
        if (v) {
            // 개행 문자를 <br/>로 치환 후 신뢰할 수 있는 HTML로 마킹
            $scope.trustedContent = $sce.trustAsHtml(v.replace(/\n/g, '<br/>'));
        }
    });

    // --- 게시글 고정 관련 함수들 (관리자 전용) ---
    /**
     * 게시글 고정 함수. HTML의 '고정하기' 버튼 클릭 시 호출됨 (ng-click="pinPost()")
     * order 값은 1로 고정 (우선순위 정책 단순화)
     */
    $scope.pinPost = function () {
        // 고정 순서(order) 값을 1로 고정
        const order = 1;

        $http
            .put('/api/posts/' + postId + '/pin', { order: order })
            .then(function () {
                alert('게시글이 고정되었습니다.');
                // 고정 후 상세 정보 다시 로드 (상태 반영)
                fetchPostDetails();
            })
            .catch(function (error) {
                if (error.status === 403) {
                    // 403 Forbidden (권한 문제 또는 개수 제한 문제)
                    alert('게시글 고정 실패: 권한이 없거나 최대 3개까지만 고정할 수 있습니다.');
                } else {
                    alert('게시글 고정 중 오류가 발생했습니다.');
                }
                console.error('Pin post failed:', error);
            });
    };

    /**
     * 게시글 고정 해제 함수. HTML의 '고정 해제' 버튼 클릭 시 호출됨 (ng-click="unpinPost()")
     */
    $scope.unpinPost = function () {
        if (confirm('게시글 고정을 해제하시겠습니까?')) {
            $http
                .put('/api/posts/' + postId + '/unpin')
                .then(function () {
                    alert('게시글 고정이 해제되었습니다.');
                    // 해제 후 상세 정보 다시 로드
                    fetchPostDetails();
                })
                .catch(function (error) {
                    if (error.status === 403) {
                        alert('고정 해제 실패: 권한이 없습니다.');
                    } else {
                        alert('고정 해제 중 오류가 발생했습니다.');
                    }
                    console.error('Unpin post failed:', error);
                });
        }
    };

    // --- 게시글 좋아요 관련 함수들 ---

    // 게시글 좋아요 개수 조회 (상세 화면용)
    $scope.loadLikeCountForPost = function (post) {
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
                post.liked = res.data.liked; // 현재 좋아요 상태
                post.likeCount = res.data.count; // 총 좋아요 개수
            });
    };

    // --- 댓글 관련 함수들 ---

    // 댓글/대댓글 트리에 좋아요 정보 적용
    $scope.applyLikeInfoToComments = function (commentList) {
        if (!commentList) return;
        commentList.forEach(function (c) {
            // 각 댓글에 좋아요 개수 적용
            $scope.loadLikeCountForComment(c);
            if (c.replies && c.replies.length > 0) {
                // 대댓글이 있으면 재귀 호출
                $scope.applyLikeInfoToComments(c.replies);
            }
        });
    };

    // 댓글/대댓글 좋아요 개수 조회
    $scope.loadLikeCountForComment = function (comment) {
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

    // 댓글/대댓글 좋아요 토글
    $scope.toggleCommentLike = function (comment) {
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

    // ▼▼▼ 대댓글 지원: submitComment 함수가 parentId와 commentData를 받도록 구성 ▼▼▼
    /**
     * '댓글 등록' 또는 '답글 등록' 버튼 클릭 시 실행될 함수
     * @param {number|null} parentId - 부모 댓글 ID. 최상위 댓글은 null.
     * @param {object} commentData - 댓글 내용이 담긴 객체. (예: { content: "..." })
     */
    $scope.submitComment = function (parentId, commentData) {
        // 서버로 전송할 댓글 데이터 객체(payload) 생성
        var commentToSend = {
            content: commentData.content, // 댓글 내용
            parent_comment_id: parentId, // 부모 ID (null일 수도 있음)
        };

        // /api/posts/{postId}/comments 로 POST 요청
        $http
            .post('/api/posts/' + postId + '/comments', commentToSend)
            .then(function () {
                // 최상위 댓글 입력창만 비움
                if (parentId === null) {
                    $scope.newComment.content = '';
                }
                // 댓글 목록 다시 로드하여 화면 갱신
                fetchComments();
            })
            .catch(function () {
                alert('댓글 등록 실패');
            });
    };
    // ▲▲▲ submitComment 함수 구성 완료 ▲▲▲

    // (HTML에서 ng-if="canModifyComment(comment)"로 사용)
    // 댓글 수정/삭제 권한 확인 함수
    $scope.canModifyComment = function (c) {
        return (
            ($rootScope.currentUser && $rootScope.currentUser.role === 'ADMIN') ||
            c.user_id === $rootScope.currentUser.username
        );
    };

    // 댓글 '삭제' 버튼 클릭 시 실행될 함수
    $scope.deleteComment = function (cId) {
        if (confirm('댓글을 삭제하시겠습니까?')) {
            $http.delete('/api/comments/' + cId).then(function () {
                // 삭제 후 목록 새로고침
                fetchComments();
            });
        }
    };

    // 댓글 '수정' 버튼 클릭 시 실행될 함수 (수정 모드 진입)
    $scope.switchToCommentEditMode = function (c) {
        c.isEditing = true; // 수정 모드 표시용 플래그
        c.editContent = c.content; // 원본 내용을 수정용 버퍼에 복사
    };

    // 댓글 '저장' 버튼 클릭 시 실행될 함수
    $scope.saveCommentChanges = function (c) {
        $http.put('/api/comments/' + c.comment_id, { content: c.editContent }).then(function () {
            c.isEditing = false; // 저장 후 보기 모드로 전환
            fetchComments(); // 목록 새로고침
        });
    };

    // 댓글 수정 '취소' 버튼 클릭 시 실행될 함수
    $scope.cancelCommentEdit = function (c) {
        c.isEditing = false; // 보기 모드로 전환
    };
}); // BoardDetailController 정의 끝

// BoardEditController (수정 전용)
// 기존 게시글 수정, 첨부파일 추가/삭제를 담당 + Summernote 에디터 사용
app.controller('BoardEditController', function ($scope, $http, $routeParams, $location) {
    const postId = $routeParams.postId; // URL에서 수정할 게시글 ID 추출

    // 1. $scope 변수 초기화
    $scope.post = {};            // 게시글(제목, 내용) 데이터
    $scope.fileList = [];        // 기존 첨부 파일 목록
    $scope.newFiles = [];        // 새로 추가할 파일 목록
    $scope.deletedFileIds = [];  // 삭제할 파일 ID 목록

    // ─────────────────────────────
    // 1-1. 드래그 앤 드롭으로 들어온 파일을 newFiles 배열에 넣는 함수
    // ─────────────────────────────
    function addFilesToNewFiles(fileList) {
        if (!fileList || !fileList.length) return;

        for (var i = 0; i < fileList.length; i++) {
            var f = fileList[i];
            $scope.newFiles.push(f);
        }
    }

    // ─────────────────────────────
    // 2. Summernote 에디터 초기화 (board-edit.html의 ng-init="initEditor()"에서 호출)
    // ─────────────────────────────
    $scope.initEditor = function () {
        setTimeout(function () {
            var $editor = $('#postContent');
            if (!$editor.length) return;

            // 이미 초기화된 경우 중복 실행 방지
            if ($editor.data('summernote-initialized')) {
                return;
            }

            $editor.summernote({
                height: 400,
                lang: 'ko-KR',
                callbacks: {
                    // 에디터 내용 변경 시 post.content에 반영
                    onChange: function (contents) {
                        $scope.$applyAsync(function () {
                            $scope.post.content = contents;
                        });
                    },
                    // 에디터에서 이미지 업로드가 발생했을 때
                    onImageUpload: function (files) {
                        if (files && files.length > 0) {
                            $scope.uploadEditorImage(files[0]);
                        }
                    },
                },
            });

            // 이미 post.content가 채워져 있으면 에디터에 반영
            if ($scope.post && $scope.post.content) {
                $editor.summernote('code', $scope.post.content);
            }

            $editor.data('summernote-initialized', true);
        }, 0);
    };

    // ─────────────────────────────
    // 3. 에디터 내부 이미지 업로드 → /api/editor-images 사용
    // ─────────────────────────────
    $scope.uploadEditorImage = function (file) {
        if (!file) return;

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
                    // 업로드 성공 시 에디터에 이미지 삽입
                    $('#postContent').summernote('insertImage', data.url);
                } else {
                    alert('이미지 업로드에 실패했습니다.');
                }
            })
            .catch(function (error) {
                console.error('에디터 이미지 업로드 실패:', error);
                alert('이미지 업로드 중 오류가 발생했습니다.');
            });
    };

    // ─────────────────────────────
    // 4. (로딩) 게시글 상세 정보 가져오기 (제목, 내용 채우기) - 수정용 API 사용
    // ─────────────────────────────
    $http
        .get('/api/posts/' + postId + '/edit') // 조회수 증가 없는 수정용 API
        .then(function (response) {
            $scope.post = response.data || {};

            // 에디터가 이미 초기화되어 있다면 내용 채워 넣기
            var $editor = $('#postContent');
            if ($editor.length && typeof $editor.summernote === 'function') {
                if ($editor.data('summernote-initialized')) {
                    $editor.summernote('code', $scope.post.content || '');
                }
            }
        })
        .catch(function () {
            alert('게시글 정보를 불러오는데 실패했습니다.');
            $location.path('/board');
        });

    // ─────────────────────────────
    // 5. (로딩) 기존 첨부파일 목록 가져오기
    // ─────────────────────────────
    $http.get('/api/posts/' + postId + '/files').then(function (response) {
        $scope.fileList = response.data || [];
        // 삭제 체크박스 초기화
        $scope.fileList.forEach(function (f) {
            f._delete = false;
        });
    });

    // ─────────────────────────────
    // 6. 수정 화면용 드래그 앤 드롭 DropZone 초기화 함수
    // ─────────────────────────────
    function initFileDropZoneEdit() {
        var dropZone = document.getElementById('fileDropZoneEdit');
        if (!dropZone) {
            // 화면에 드롭존 요소가 없으면 종료
            return;
        }

        // 기본 이벤트 방지 함수
        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }

        // dragenter, dragover, dragleave, drop 이벤트 모두 기본 동작 막기
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (eventName) {
            dropZone.addEventListener(eventName, preventDefaults, false);
        });

        // 드래그 중일 때 스타일 변경
        dropZone.addEventListener('dragover', function () {
            dropZone.classList.add('drag-over');
        });

        // 드래그가 영역을 벗어날 때 스타일 복원
        dropZone.addEventListener('dragleave', function () {
            dropZone.classList.remove('drag-over');
        });

        // 파일 드롭 시 실행
        dropZone.addEventListener('drop', function (e) {
            dropZone.classList.remove('drag-over');

            var files = e.dataTransfer && e.dataTransfer.files;
            if (!files || !files.length) {
                return;
            }

            // Angular 스코프 업데이트
            $scope.$apply(function () {
                addFilesToNewFiles(files);
            });
        });
    }

    // 뷰(html)가 로드된 후 dropzone 활성화
    $scope.$on('$viewContentLoaded', function () {
        initFileDropZoneEdit();
    });

    // ─────────────────────────────
    // 7. (액션) 수정 완료 버튼 클릭
    // ─────────────────────────────
    $scope.saveChanges = function () {
        if (!confirm('수정하시겠습니까?')) {
            return;
        }

        // 에디터의 최신 내용을 content에 반영
        var editorHtml;
        var $editor = $('#postContent');
        if ($editor.length && typeof $editor.summernote === 'function') {
            editorHtml = $editor.summernote('code');
        } else {
            editorHtml = $scope.post.content || '';
        }
        $scope.post.content = editorHtml || '';

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

        // 4) PUT 전송으로 게시글 수정
        $http
            .put('/api/posts/' + postId, formData, {
                transformRequest: angular.identity,
                headers: { 'Content-Type': undefined },
            })
            .then(function () {
                alert('게시글이 수정되었습니다.');
                // ✅ 수정 후 상세보기 페이지로 이동하되, 조회수는 증가시키지 않도록 fromEdit=true 전달
                $location.path('/board/' + postId).search({ fromEdit: 'true' });
            })
            .catch(function (error) {
                alert('게시글 수정 중 오류가 발생했습니다.');
                console.error('Post update failed:', error);
            });
    }; // saveChanges 끝

    // ─────────────────────────────
    // 8. (액션) 취소 버튼 클릭
    // ─────────────────────────────
    $scope.cancelEdit = function () {
        // 수정 취소 후 상세보기 페이지로 이동 (이때도 조회수 증가 없이 보기)
        $location.path('/board/' + postId).search({ fromEdit: 'true' });
    };
}); // BoardEditController 정의 끝

// file-model 디렉티브 (공통)
// input[type="file"] 요소의 FileList를 scope 변수에 바인딩하기 위한 디렉티브
// board-new.html / board-detail.html 등에서 사용
app.directive('fileModel', [
    '$parse',
    function ($parse) {
        return {
            restrict: 'A', // attribute로 사용 (예: file-model="uploadFiles")
            link: function (scope, element, attrs) {
                // file-model에 지정된 표현식(예: "uploadFiles")을 파싱
                var model = $parse(attrs.fileModel);
                var modelSetter = model.assign; // scope 변수에 값을 할당하는 setter

                // 파일 선택이 변경되었을 때(change 이벤트) 실행
                element.bind('change', function () {
                    scope.$apply(function () {
                        // input[type=file]의 FileList를 그대로 scope 변수에 할당
                        modelSetter(scope, element[0].files);
                    });
                });
            },
        };
    },
]); // fileModel 디렉티브 끝

// 파일 다운로드뷰 컨트롤러
// 파일 상세 정보 조회, 이미지 미리보기, 다운로드 링크 제공을 담당
app.controller('FileViewController', function ($scope, $routeParams, $http, $window) {
    $scope.file = null; // 파일 메타데이터 (파일명, 타입, 크기 등)
    $scope.isImage = false; // 이미지 여부 (이미지일 경우 미리보기 가능)
    $scope.viewUrl = ''; // 파일 표시용 URL (/api/files/{id}/view)
    $scope.downloadUrl = ''; // 파일 다운로드 URL (/api/files/{id}/download)

    var fileId = $routeParams.fileId; // URL에서 파일 ID 추출

    // 파일 메타데이터 조회
    $http
        .get('/api/files/' + fileId + '/meta')
        .then(function (response) {
            $scope.file = response.data;

            // Content-Type을 기준으로 이미지 여부 판단
            var contentType = $scope.file.content_type || '';
            $scope.isImage = contentType.indexOf('image') === 0;

            // 뷰어용 URL, 다운로드용 URL 설정
            $scope.viewUrl = '/api/files/' + fileId + '/view';
            // 오타 대비용 코드가 있었지만, 실제 사용은 아래 한 줄로 충분
            $scope.downloadUrl = '/api.files/' + fileId + '/download'.replace('/api.files', '/api/files'); // 혹시 오타 대비
            $scope.downloadUrl = '/api/files/' + fileId + '/download'; // 실제로 사용할 깔끔한 다운로드 URL
        })
        .catch(function (error) {
            console.error('파일 정보를 불러오는 중 오류:', error);
            alert('파일 정보를 불러오는 중 오류가 발생했습니다.');
        });

    // 뒤로가기 (브라우저 히스토리 되돌리기)
    $scope.goBack = function () {
        $window.history.back();
    };
});

// 수정됨 끝
