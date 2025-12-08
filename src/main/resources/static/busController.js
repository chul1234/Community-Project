// 수정됨: TAGO 노선/정류장/버스 위치(JSON) + NGII 지도 마커 + 자동 새로고침 ON/OFF 버튼

app.controller('BusController', function ($scope, $http, $timeout, $interval) {

    const CITY_CODE = '25'; // 대전

    // 검색어 (버스 번호)
    $scope.searchTerm = '';

    // 지도 객체
    $scope.map1 = null;

    // JSON 디버깅용 텍스트박스 바인딩
    $scope.routeResultJson    = '';   // 노선 조회 결과
    $scope.stopsResultJson    = '';   // 정류장 목록
    $scope.locationResultJson = '';   // 버스 위치 정보

    // 현재 선택된 노선ID
    $scope.currentRouteId = null;

    // 정류장 리스트 (파싱된 배열)
    $scope.stops = [];

    // 자동 갱신 타이머 핸들
    var autoRefreshPromise = null;

    // 자동 새로고침 상태 (버튼 disabled 표시용)
    $scope.isAutoRefreshOn = false;

    // -------------------------
    // 공통: JSON 문자열 → 객체 변환
    // -------------------------
    function parseMaybeJson(data) {
        if (angular.isObject(data)) return data;
        if (!data) return null;
        try {
            return JSON.parse(data);
        } catch (e) {
            console.error('JSON 파싱 실패:', e, data);
            return null;
        }
    }

    // -------------------------
    // 지도 초기화
    // -------------------------
    $scope.initMap = function () {
        var mapDiv = document.getElementById('map1');

        if (!window.ngii_wmts || !mapDiv) {
            console.error('NGII 지도 스크립트 또는 #map1 요소를 찾을 수 없습니다.', window.ngii_wmts, mapDiv);
            return;
        }

        // NGII WMTS 지도 생성
        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 3
        });

        console.log('NGII map 초기화 완료:', $scope.map1);
    };

    // Angular 뷰 로딩 후 지도 초기화
    $timeout($scope.initMap, 0);

    // -------------------------
    // 정류장 마커
    // -------------------------
    var stopMarkers = [];

    function clearStopMarkers() {
        stopMarkers.forEach(function (m) {
            try {
                if (m && m.setMap) {
                    m.setMap(null);
                } else if ($scope.map1 && $scope.map1.removeOverlay) {
                    $scope.map1.removeOverlay(m);
                }
            } catch (e) {
                console.warn('정류장 마커 제거 중 오류:', e);
            }
        });
        stopMarkers = [];
    }

    function addStopMarker(lat, lon, title) {
        if (!$scope.map1) return;

        try {
            if (window.ngii_wmts && ngii_wmts.Marker && ngii_wmts.LatLng) {
                var marker = new ngii_wmts.Marker({
                    position: new ngii_wmts.LatLng(lat, lon),
                    map: $scope.map1,
                    title: title || ''
                });
                stopMarkers.push(marker);
            } else {
                console.warn('ngii_wmts.Marker / LatLng 가 없습니다. 마커를 생성할 수 없습니다.');
            }
        } catch (e) {
            console.warn('정류장 NGII 마커 생성 오류:', e);
        }
    }

    function drawStopsOnMap(stops) {
        clearStopMarkers();
        if (!stops || !stops.length) return;

        stops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                addStopMarker(lat, lon, s.nodenm || s.stationName || '');
            }
        });
    }

    // -------------------------
    // 버스 위치 마커
    // -------------------------
    var busMarkers = [];

    function clearBusMarkers() {
        busMarkers.forEach(function (m) {
            try {
                if (m && m.setMap) {
                    m.setMap(null);
                } else if ($scope.map1 && $scope.map1.removeOverlay) {
                    $scope.map1.removeOverlay(m);
                }
            } catch (e) {
                console.warn('버스 마커 제거 중 오류:', e);
            }
        });
        busMarkers = [];
    }

    function addBusMarker(lat, lon, title) {
        if (!$scope.map1) return;

        try {
            if (window.ngii_wmts && ngii_wmts.Marker && ngii_wmts.LatLng) {
                var marker = new ngii_wmts.Marker({
                    position: new ngii_wmts.LatLng(lat, lon),
                    map: $scope.map1,
                    title: title || ''
                });
                busMarkers.push(marker);
            } else {
                console.warn('ngii_wmts.Marker / LatLng 가 없습니다. 버스 마커를 생성할 수 없습니다.');
            }
        } catch (e) {
            console.warn('버스 NGII 마커 생성 오류:', e);
        }
    }

    // 네가 보여준 JSON(gpslati, gpslong, vehicleno, routenm)에 맞춰서 마커 생성
    function drawBusLocationsOnMap(busItems) {
        clearBusMarkers();
        if (!busItems || !busItems.length) return;

        busItems.forEach(function (b) {
            var lat = parseFloat(b.gpslati);
            var lon = parseFloat(b.gpslong);

            if (!isNaN(lat) && !isNaN(lon)) {
                var label = (b.vehicleno || '') + ' / ' + (b.routenm || '');
                addBusMarker(lat, lon, label.trim());
            }
        });
    }

    // -------------------------
    // 30초 자동 갱신 (버스 위치만)
    // -------------------------
    function cancelAutoRefresh() {
        if (autoRefreshPromise) {
            $interval.cancel(autoRefreshPromise);
            autoRefreshPromise = null;
        }
        $scope.isAutoRefreshOn = false;
    }

    function startAutoRefresh() {
        cancelAutoRefresh();

        if (!$scope.currentRouteId) return;

        autoRefreshPromise = $interval(function () {
            $scope.fetchBusLocations();
        }, 30000); // 30초

        $scope.isAutoRefreshOn = true;
    }

    // 탭(라우트) 떠날 때 자동 갱신 중지
    $scope.$on('$destroy', function () {
        cancelAutoRefresh();
    });

    // --- 여기부터 버튼에서 쓸 공개 함수 ---

    // [버튼용] 자동 새로고침 시작(30초마다)
    $scope.enableAutoRefresh = function () {
        if (!$scope.currentRouteId) {
            alert('먼저 버스 번호를 검색해서 노선을 선택하세요.');
            return;
        }
        startAutoRefresh();
    };

    // [버튼용] 자동 새로고침 정지
    $scope.disableAutoRefresh = function () {
        cancelAutoRefresh();
    };

    // =========================
    // 1단계: 버스 번호 → 노선 조회
    // =========================
    $scope.searchBus = function () {
        if (!$scope.searchTerm) {
            alert('버스 번호를 입력하세요.');
            return;
        }

        var routeNo = $scope.searchTerm;

        $http.get('/api/bus/routes', {
            params: { routeNo: routeNo }
        }).then(function (res) {

            // 1칸: 노선 조회 결과 원본 찍기
            if (angular.isString(res.data)) {
                $scope.routeResultJson = res.data;
            } else {
                $scope.routeResultJson = JSON.stringify(res.data, null, 2);
            }

            var data = parseMaybeJson(res.data);
            if (!data || !data.response || !data.response.body) {
                console.warn('노선 응답 구조가 예상과 다름:', data);
                alert('노선 정보를 찾을 수 없습니다. (응답 구조 확인 필요)');
                return;
            }

            var items = data.response.body.items && data.response.body.items.item;
            if (!items) {
                alert('노선 목록이 비어 있습니다.');
                return;
            }

            var first = angular.isArray(items) ? items[0] : items;
            var routeId = first.routeid || first.routeId;
            if (!routeId) {
                alert('응답에서 routeId 를 찾을 수 없습니다.');
                return;
            }

            $scope.currentRouteId = routeId;

            // 2단계: 해당 노선의 정류장 목록 조회
            $scope.fetchRouteStops(routeId);

            // 3단계: 버스 위치 조회
            $scope.fetchBusLocations();

            // 검색 시에도 자동 새로고침 바로 시작하고 싶으면 유지
            startAutoRefresh();

        }).catch(function (err) {
            console.error('노선 조회 실패:', err);

            var msg = (err && err.data)
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.routeResultJson = 'ERROR: ' + msg;

            alert('노선 정보를 가져오지 못했습니다.');
        });
    };

    // =========================
    // 2단계: 노선ID → 경유 정류장 목록 조회
    // =========================
    $scope.fetchRouteStops = function (routeId) {
        if (!routeId) return;

        $http.get('/api/bus/route-stops', {
            params: { routeId: routeId }
        }).then(function (res) {

            // 2칸: 정류장 목록 원본 찍기
            if (angular.isString(res.data)) {
                $scope.stopsResultJson = res.data;
            } else {
                $scope.stopsResultJson = JSON.stringify(res.data, null, 2);
            }

            var data = parseMaybeJson(res.data);
            if (!data || !data.response || !data.response.body) {
                console.warn('정류장 응답 구조가 예상과 다름:', data);
                alert('정류장 정보를 찾을 수 없습니다.');
                return;
            }

            var items = data.response.body.items && data.response.body.items.item;
            if (!items) {
                alert('정류장 목록이 비어 있습니다.');
                return;
            }

            var stopsArray = angular.isArray(items) ? items : [items];
            $scope.stops = stopsArray;

            // 지도에 정류장 마커 찍기
            drawStopsOnMap(stopsArray);

        }).catch(function (err) {
            console.error('정류장 목록 조회 실패:', err);

            var msg = (err && err.data)
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.stopsResultJson = 'ERROR: ' + msg;

            alert('정류장 정보를 가져오지 못했습니다.');
        });
    };

    // =========================
    // 3단계: 현재 routeId 기준 버스 위치 조회
    // =========================
    $scope.fetchBusLocations = function () {
        if (!$scope.currentRouteId) {
            return;
        }

        $http.get('/api/bus/locations', {
            params: {
                routeId: $scope.currentRouteId,
                pageNo: 1,
                numOfRows: 100
            }
        }).then(function (res) {

            // 3칸: 버스 위치 JSON 원본 찍기
            if (angular.isString(res.data)) {
                $scope.locationResultJson = res.data;
            } else {
                $scope.locationResultJson = JSON.stringify(res.data, null, 2);
            }

            var data = parseMaybeJson(res.data);
            if (!data || !data.response || !data.response.body) {
                console.warn('버스 위치 응답 구조가 예상과 다름:', data);
                clearBusMarkers();
                return;
            }

            var items = data.response.body.items && data.response.body.items.item;
            if (!items) {
                console.warn('버스 위치 목록이 비어 있음');
                clearBusMarkers();
                return;
            }

            var busArray = angular.isArray(items) ? items : [items];

            // 지도에 버스 위치 마커 찍기
            drawBusLocationsOnMap(busArray);

        }).catch(function (err) {
            console.error('버스 위치 조회 실패:', err);

            var msg = (err && err.data)
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.locationResultJson = 'ERROR: ' + msg;
        });
    };

});

// 수정됨 끝
