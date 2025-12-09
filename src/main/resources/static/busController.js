// 수정됨: TAGO 노선/정류장/버스 위치(JSON) + NGII 지도 마커 + 자동 새로고침 ON/OFF 버튼

// AngularJS 컨트롤러 정의 및 의존성 서비스 주입
app.controller('BusController', function ($scope, $http, $timeout, $interval) {

    // 대전광역시 도시 코드 상수 선언
    const CITY_CODE = '25'; // 대전

    // 검색어 (버스 번호)
    // 화면 입력창과 바인딩되는 검색어 변수 초기화
    $scope.searchTerm = '';

    // 지도 객체
    // 지도 인스턴스 저장을 위한 변수 초기화
    $scope.map1 = null;

    // JSON 디버깅용 텍스트박스 바인딩
    // 노선 조회 결과 JSON 문자열 저장 변수 선언
    $scope.routeResultJson    = '';   // 노선 조회 결과
    // 정류장 목록 JSON 문자열 저장 변수 선언
    $scope.stopsResultJson    = '';   // 정류장 목록
    // 버스 위치 정보 JSON 문자열 저장 변수 선언
    $scope.locationResultJson = '';   // 버스 위치 정보

    // 현재 선택된 노선ID
    // 노선 고유 ID 저장을 위한 변수 초기화
    $scope.currentRouteId = null;

    // 정류장 리스트 (파싱된 배열)
    // 정류장 데이터 배열 저장을 위한 변수 초기화
    $scope.stops = [];

    // 자동 갱신 타이머 핸들
    // 타이머 제어를 위한 핸들 변수 선언
    var autoRefreshPromise = null;

    // 자동 새로고침 상태 (버튼 disabled 표시용)
    // 자동 갱신 활성화 여부 플래그 초기화
    $scope.isAutoRefreshOn = false;

    // -------------------------
    // 공통: JSON 문자열 → 객체 변환
    // -------------------------
    // 데이터 파싱 헬퍼 함수 정의
    function parseMaybeJson(data) {
        // 데이터가 이미 객체인 경우 반환
        if (angular.isObject(data)) return data;
        // 데이터가 없는 경우 null 반환
        if (!data) return null;
        // 파싱 예외 처리 구문 시작
        try {
            // JSON 문자열 파싱 및 객체 반환
            return JSON.parse(data);
        // 에러 발생 시 처리
        } catch (e) {
            // 파싱 실패 로그 출력
            console.error('JSON 파싱 실패:', e, data);
            // null 반환
            return null;
        }
    }

    // -------------------------
    // 지도 초기화
    // -------------------------
    // 지도 생성 함수 정의
    $scope.initMap = function () {
        // 지도 컨테이너 DOM 요소 조회
        var mapDiv = document.getElementById('map1');

        // 라이브러리 로드 여부 및 DOM 요소 존재 확인
        if (!window.ngii_wmts || !mapDiv) {
            // 실패 시 에러 로그 출력
            console.error('NGII 지도 스크립트 또는 #map1 요소를 찾을 수 없습니다.', window.ngii_wmts, mapDiv);
            // 함수 종료
            return;
        }

        // NGII WMTS 지도 생성
        // 지도 객체 생성 및 스코프 변수 할당
        $scope.map1 = new ngii_wmts.map('map1', {
            // 초기 줌 레벨 설정
            zoom: 3
        });

        // 초기화 완료 로그 출력
        console.log('NGII map 초기화 완료:', $scope.map1);
    };

    // Angular 뷰 로딩 후 지도 초기화
    // 렌더링 완료 후 지도 생성 함수 지연 실행
    $timeout($scope.initMap, 0);

    // -------------------------
    // 정류장 마커
    // -------------------------
    // 정류장 마커 객체 관리 배열 선언
    var stopMarkers = [];

    // 정류장 마커 제거 함수 정의
    function clearStopMarkers() {
        // 마커 배열 순회 시작
        stopMarkers.forEach(function (m) {
            // 예외 처리 구문 시작
            try {
                // 마커 객체 및 setMap 메서드 존재 확인
                if (m && m.setMap) {
                    // 지도에서 마커 제거 실행
                    m.setMap(null);
                // removeOverlay 메서드 존재 확인 (대체 방식)
                } else if ($scope.map1 && $scope.map1.removeOverlay) {
                    // 오버레이 제거 실행
                    $scope.map1.removeOverlay(m);
                }
            // 에러 발생 시 처리
            } catch (e) {
                // 제거 실패 경고 로그 출력
                console.warn('정류장 마커 제거 중 오류:', e);
            }
        });
        // 마커 배열 초기화
        stopMarkers = [];
    }

    // 정류장 마커 추가 함수 정의
    function addStopMarker(lat, lon, title) {
        // 지도 객체 존재 여부 확인
        if (!$scope.map1) return;

        // 예외 처리 구문 시작
        try {
            // 지도 라이브러리 필수 객체 존재 확인
            if (window.ngii_wmts && ngii_wmts.Marker && ngii_wmts.LatLng) {
                // 마커 객체 생성
                var marker = new ngii_wmts.Marker({
                    // 좌표 객체 생성 및 설정
                    position: new ngii_wmts.LatLng(lat, lon),
                    // 표시할 지도 객체 설정
                    map: $scope.map1,
                    // 마커 툴팁 제목 설정
                    title: title || ''
                });
                // 생성된 마커 배열에 추가
                stopMarkers.push(marker);
            // 필수 객체 부재 시 처리
            } else {
                // 경고 로그 출력
                console.warn('ngii_wmts.Marker / LatLng 가 없습니다. 마커를 생성할 수 없습니다.');
            }
        // 에러 발생 시 처리
        } catch (e) {
            // 생성 실패 경고 로그 출력
            console.warn('정류장 NGII 마커 생성 오류:', e);
        }
    }

    // 정류장 일괄 표시 함수 정의
    function drawStopsOnMap(stops) {
        // 기존 마커 제거 함수 호출
        clearStopMarkers();
        // 데이터 존재 여부 및 배열 길이 확인
        if (!stops || !stops.length) return;

        // 정류장 데이터 배열 순회 시작
        stops.forEach(function (s) {
            // 위도 데이터 파싱 및 변환
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            // 경도 데이터 파싱 및 변환
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            // 좌표 유효성 검사 (숫자 여부)
            if (!isNaN(lat) && !isNaN(lon)) {
                // 마커 추가 함수 호출 (이름 데이터 포함)
                addStopMarker(lat, lon, s.nodenm || s.stationName || '');
            }
        });
    }

    // -------------------------
    // 버스 위치 마커
    // -------------------------
    // 버스 마커 객체 관리 배열 선언
    var busMarkers = [];

    // 버스 마커 제거 함수 정의
    function clearBusMarkers() {
        // 마커 배열 순회 시작
        busMarkers.forEach(function (m) {
            // 예외 처리 구문 시작
            try {
                // 마커 객체 및 setMap 메서드 존재 확인
                if (m && m.setMap) {
                    // 지도에서 마커 제거 실행
                    m.setMap(null);
                // removeOverlay 메서드 존재 확인
                } else if ($scope.map1 && $scope.map1.removeOverlay) {
                    // 오버레이 제거 실행
                    $scope.map1.removeOverlay(m);
                }
            // 에러 발생 시 처리
            } catch (e) {
                // 제거 실패 경고 로그 출력
                console.warn('버스 마커 제거 중 오류:', e);
            }
        });
        // 마커 배열 초기화
        busMarkers = []; // 배열 초기화
    }

    // 버스 마커 추가 함수 정의
    function addBusMarker(lat, lon, title) {
        // 지도 객체 존재 여부 확인
        if (!$scope.map1) return;

        // 예외 처리 구문 시작
        try {
            // 지도 라이브러리 필수 객체 존재 확인
            if (window.ngii_wmts && ngii_wmts.Marker && ngii_wmts.LatLng) {
                // 마커 객체 생성
                var marker = new ngii_wmts.Marker({
                    // 좌표 객체 생성 및 설정
                    position: new ngii_wmts.LatLng(lat, lon),
                    // 표시할 지도 객체 설정
                    map: $scope.map1,
                    // 마커 툴팁 제목 설정
                    title: title || ''
                });
                // 생성된 마커 배열에 추가
                busMarkers.push(marker); // 버스 마커 배열에 추가
            // 필수 객체 부재 시 처리
            } else {
                // 경고 로그 출력
                console.warn('ngii_wmts.Marker / LatLng 가 없습니다. 버스 마커를 생성할 수 없습니다.');
            }
        // 에러 발생 시 처리
        } catch (e) {
            // 생성 실패 경고 로그 출력
            console.warn('버스 NGII 마커 생성 오류:', e);
        }
    }

    // 네가 보여준 JSON(gpslati, gpslong, vehicleno, routenm)에 맞춰서 마커 생성
    // 버스 위치 일괄 표시 함수 정의
    function drawBusLocationsOnMap(busItems) {
        // 기존 버스 마커 제거 함수 호출
        clearBusMarkers();
        // 데이터 존재 여부 및 배열 길이 확인
        if (!busItems || !busItems.length) return;

        // 버스 데이터 배열 순회 시작
        busItems.forEach(function (b) {
            // 위도 데이터 파싱 및 변환
            var lat = parseFloat(b.gpslati);
            // 경도 데이터 파싱 및 변환
            var lon = parseFloat(b.gpslong);

            // 좌표 유효성 검사 (숫자 여부)
            if (!isNaN(lat) && !isNaN(lon)) {
                // 마커 라벨 문자열 조합 (차량번호 + 노선명)
                var label = (b.vehicleno || '') + ' / ' + (b.routenm || '');
                // 마커 추가 함수 호출 (공백 제거 적용)
                addBusMarker(lat, lon, label.trim());
            }
        });
    }

    // -------------------------
    // 30초 자동 갱신 (버스 위치만)
    // -------------------------
    // 자동 갱신 중지 함수 정의
    function cancelAutoRefresh() {
        // 타이머 실행 여부 확인
        if (autoRefreshPromise) {
            // 인터벌 타이머 취소 실행
            $interval.cancel(autoRefreshPromise);
            // 타이머 핸들 초기화
            autoRefreshPromise = null;
        }
        // 자동 갱신 상태 플래그 변경 (OFF)
        $scope.isAutoRefreshOn = false;
    }

    // 자동 갱신 시작 함수 정의
    function startAutoRefresh() {
        // 기존 타이머 취소 함수 호출
        cancelAutoRefresh();

        // 선택된 노선 ID 존재 여부 확인
        if (!$scope.currentRouteId) return;

        // 30초 주기 반복 실행 설정 및 핸들 저장
        autoRefreshPromise = $interval(function () {
            // 버스 위치 조회 함수 호출
            $scope.fetchBusLocations();
        }, 30000); // 30초

        // 자동 갱신 상태 플래그 변경 (ON)
        $scope.isAutoRefreshOn = true;
    }

    // 탭(라우트) 떠날 때 자동 갱신 중지
    // 소멸 이벤트 리스너 등록
    $scope.$on('$destroy', function () {
        // 타이머 정리 함수 호출
        cancelAutoRefresh();
    });

    // --- 여기부터 버튼에서 쓸 공개 함수 ---

    // [버튼용] 자동 새로고침 시작(30초마다)
    // 자동 갱신 활성화 버튼 핸들러 정의
    $scope.enableAutoRefresh = function () {
        // 노선 미선택 시 처리
        if (!$scope.currentRouteId) {
            // 경고 알림 출력
            alert('먼저 버스 번호를 검색해서 노선을 선택하세요.');
            // 함수 종료
            return;
        }
        // 자동 갱신 시작 함수 호출
        startAutoRefresh();
    };

    // [버튼용] 자동 새로고침 정지
    // 자동 갱신 비활성화 버튼 핸들러 정의
    $scope.disableAutoRefresh = function () {
        // 자동 갱신 중지 함수 호출
        cancelAutoRefresh();
    };

    // =========================
    // 1단계: 버스 번호 → 노선 조회
    // =========================
    // 버스 검색 함수 정의
    $scope.searchBus = function () {
        // 검색어 입력 여부 확인
        if (!$scope.searchTerm) {
            // 미입력 시 경고 알림 출력
            alert('버스 번호를 입력하세요.');
            // 함수 종료
            return;
        }

        // 검색어 변수 할당
        var routeNo = $scope.searchTerm;

        // 노선 조회 API GET 요청 실행
        $http.get('/api/bus/routes', {
            // 파라미터 설정 (버스 번호)
            params: { routeNo: routeNo }
        }).then(function (res) {

            // 1칸: 노선 조회 결과 원본 찍기
            // 응답 데이터 타입 확인 (문자열 여부)
            if (angular.isString(res.data)) {
                // 문자열 데이터 할당
                $scope.routeResultJson = res.data;
            // 객체인 경우 처리
            } else {
                // JSON 문자열 변환 후 할당
                $scope.routeResultJson = JSON.stringify(res.data, null, 2);
            }

            // 응답 데이터 파싱 실행
            var data = parseMaybeJson(res.data);
            // 데이터 구조 유효성 검사 (Body 존재 여부)
            if (!data || !data.response || !data.response.body) {
                // 구조 불일치 경고 로그 출력
                console.warn('노선 응답 구조가 예상과 다름:', data);
                // 검색 실패 알림 출력
                alert('노선 정보를 찾을 수 없습니다. (응답 구조 확인 필요)');
                // 함수 종료
                return;
            }

            // 노선 아이템 목록 추출
            var items = data.response.body.items && data.response.body.items.item;
            // 아이템 부재 여부 확인
            if (!items) {
                // 목록 비어있음 알림 출력
                alert('노선 목록이 비어 있습니다.');
                // 함수 종료
                return;
            }

            // 첫 번째 아이템 추출 (배열/객체 구분 처리)
            var first = angular.isArray(items) ? items[0] : items;
            // 노선 ID 추출 (대소문자 처리)
            var routeId = first.routeid || first.routeId;
            // ID 부재 여부 확인
            if (!routeId) {
                // ID 없음 알림 출력
                alert('응답에서 routeId 를 찾을 수 없습니다.');
                // 함수 종료
                return;
            }

            // 현재 노선 ID 스코프 변수 저장
            $scope.currentRouteId = routeId;

            // 2단계: 해당 노선의 정류장 목록 조회
            // 정류장 조회 함수 호출
            $scope.fetchRouteStops(routeId);

            // 3단계: 버스 위치 조회
            // 버스 위치 조회 함수 호출
            $scope.fetchBusLocations();

            // 검색 시에도 자동 새로고침 바로 시작하고 싶으면 유지
            // 자동 갱신 시작 함수 호출
            startAutoRefresh();

        }).catch(function (err) {
            // API 에러 로그 출력
            console.error('노선 조회 실패:', err);

            // 에러 메시지 데이터 가공
            var msg = (err && err.data)
                // 데이터 존재 시 문자열 변환 처리
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                // 데이터 부재 시 상태 코드 사용
                : (err.status + ' ' + err.statusText);
            // 에러 메시지 화면 출력 변수 할당
            $scope.routeResultJson = 'ERROR: ' + msg;

            // 실패 알림 출력
            alert('노선 정보를 가져오지 못했습니다.');
        });
    };

    // =========================
    // 2단계: 노선ID → 경유 정류장 목록 조회
    // =========================
    // 정류장 목록 조회 함수 정의
    $scope.fetchRouteStops = function (routeId) {
        // 노선 ID 존재 여부 확인
        if (!routeId) return;

        // 정류장 조회 API GET 요청 실행
        $http.get('/api/bus/route-stops', {
            // 파라미터 설정 (노선 ID)
            params: { routeId: routeId }
        }).then(function (res) {

            // 2칸: 정류장 목록 원본 찍기
            // 응답 데이터 타입 확인 (문자열 여부)
            if (angular.isString(res.data)) {
                // 문자열 데이터 할당
                $scope.stopsResultJson = res.data;
            // 객체인 경우 처리
            } else {
                // JSON 문자열 변환 후 할당
                $scope.stopsResultJson = JSON.stringify(res.data, null, 2);
            }

            // 응답 데이터 파싱 실행
            var data = parseMaybeJson(res.data);
            // 데이터 구조 유효성 검사
            if (!data || !data.response || !data.response.body) {
                // 구조 불일치 경고 로그 출력
                console.warn('정류장 응답 구조가 예상과 다름:', data);
                // 조회 실패 알림 출력
                alert('정류장 정보를 찾을 수 없습니다.');
                // 함수 종료
                return;
            }

            // 정류장 아이템 목록 추출
            var items = data.response.body.items && data.response.body.items.item;
            // 아이템 부재 여부 확인
            if (!items) {
                // 목록 비어있음 알림 출력
                alert('정류장 목록이 비어 있습니다.');
                // 함수 종료
                return;
            }

            // 아이템 배열 통일 처리
            var stopsArray = angular.isArray(items) ? items : [items];
            // 정류장 배열 스코프 변수 저장
            $scope.stops = stopsArray;

            // 지도에 정류장 마커 찍기
            // 마커 표시 함수 호출
            drawStopsOnMap(stopsArray);

        }).catch(function (err) {
            // API 에러 로그 출력
            console.error('정류장 목록 조회 실패:', err);

            // 에러 메시지 데이터 가공
            var msg = (err && err.data)
                // 데이터 존재 시 문자열 변환 처리
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                // 데이터 부재 시 상태 코드 사용
                : (err.status + ' ' + err.statusText);
            // 에러 메시지 화면 출력 변수 할당
            $scope.stopsResultJson = 'ERROR: ' + msg;

            // 실패 알림 출력
            alert('정류장 정보를 가져오지 못했습니다.');
        });
    };

    // =========================
    // 3단계: 현재 routeId 기준 버스 위치 조회
    // =========================
    // 버스 위치 조회 함수 정의
    $scope.fetchBusLocations = function () {
        // 노선 ID 미존재 시 처리
        if (!$scope.currentRouteId) {
            // 함수 종료
            return;
        }

        // 위치 조회 API GET 요청 실행
        $http.get('/api/bus/locations', {
            // 파라미터 설정
            params: {
                // 노선 ID 전달
                routeId: $scope.currentRouteId,
                // 페이지 번호 전달
                pageNo: 1,
                // 조회 개수 전달
                numOfRows: 100 // 최대 100대까지 조회
            }
        }).then(function (res) {

            // 3칸: 버스 위치 JSON 원본 찍기
            // 응답 데이터 타입 확인 (문자열 여부)
            if (angular.isString(res.data)) {
                // 문자열 데이터 할당
                $scope.locationResultJson = res.data;
            // 객체인 경우 처리
            } else {
                // JSON 문자열 변환 후 할당
                $scope.locationResultJson = JSON.stringify(res.data, null, 2);
            }

            // 응답 데이터 파싱 실행
            var data = parseMaybeJson(res.data);
            // 데이터 구조 유효성 검사
            if (!data || !data.response || !data.response.body) {
                // 구조 불일치 경고 로그 출력
                console.warn('버스 위치 응답 구조가 예상과 다름:', data);
                // 마커 초기화 실행
                clearBusMarkers(); // 구조가 이상하면 기존 마커라도 지움
                // 함수 종료
                return;
            }

            // 버스 아이템 목록 추출
            var items = data.response.body.items && data.response.body.items.item;
            // 아이템 부재 여부 확인
            if (!items) {
                // 목록 비어있음 경고 로그 출력
                console.warn('버스 위치 목록이 비어 있음');
                // 마커 초기화 실행
                clearBusMarkers();
                // 함수 종료
                return;
            }

            // 아이템 배열 통일 처리
            var busArray = angular.isArray(items) ? items : [items];

            // 지도에 버스 위치 마커 찍기
            // 마커 표시 함수 호출
            drawBusLocationsOnMap(busArray);

        }).catch(function (err) {
            // API 에러 로그 출력
            console.error('버스 위치 조회 실패:', err);

            // 에러 메시지 데이터 가공
            var msg = (err && err.data)
                // 데이터 존재 시 문자열 변환 처리
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                // 데이터 부재 시 상태 코드 사용
                : (err.status + ' ' + err.statusText);
            // 에러 메시지 화면 출력 변수 할당
            $scope.locationResultJson = 'ERROR: ' + msg;
        });
    };

});

// 수정됨 끝