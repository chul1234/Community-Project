// 수정됨: TAGO 좌표 → NGII 변환 + 정류장/버스 마커 표시 + 자동 새로고침 + 정류장 영역으로 지도 자동 이동

// =========================
// EPSG:5179(UTM-K, GRS80) 좌표계 정의 + proj4 등록
// =========================
proj4.defs(
    'EPSG:5179',
    '+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 ' +
        '+x_0=1000000 +y_0=2000000 +ellps=GRS80 +units=m +no_defs'
);
ol.proj.proj4.register(proj4);

// AngularJS 컨트롤러 정의
app.controller('BusController', function ($scope, $http, $timeout, $interval) {
    // 대전 도시 코드 (현재는 백엔드에서만 사용)
    const CITY_CODE = '25';

    // 검색어(버스 번호)
    $scope.searchTerm = '';

    // NGII 지도 래퍼 객체 (ngii_wmts.map 인스턴스)
    $scope.map1 = null;

    // 실제 OpenLayers 지도 객체
    var olMap = null;

    // JSON 디버그용 바인딩
    $scope.routeResultJson = ''; // 노선 JSON
    $scope.stopsResultJson = ''; // 정류장 JSON
    $scope.locationResultJson = ''; // 버스 위치 JSON

    // 선택된 노선 ID
    $scope.currentRouteId = null;

    // 정류장 배열 (TAGO 응답 파싱 결과)
    $scope.stops = [];

    // 자동 새로고침 타이머 핸들
    var autoRefreshPromise = null;

    // 자동 새로고침 상태
    $scope.isAutoRefreshOn = false;

    // 지도 내부 로딩 플래그 (●●● 전용)
    $scope.isMapLoading = false;

    // -------------------------
    // OpenLayers 벡터 레이어 준비
    // -------------------------
    // 정류장 마커용 소스/레이어
    var stopSource = new ol.source.Vector();
    var stopLayer = new ol.layer.Vector({
        source: stopSource,
    });

    // 버스 마커용 소스/레이어
    var busSource = new ol.source.Vector();
    var busLayer = new ol.layer.Vector({
        source: busSource,
    });

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
        // HTML 상의 #map1 요소 찾기
        var mapDiv = document.getElementById('map1');

        // NGII 스크립트/DOM 점검
        if (!window.ngii_wmts || !mapDiv) {
            console.error(
                'NGII 지도 스크립트 또는 #map1 요소를 찾을 수 없습니다.',
                window.ngii_wmts,
                mapDiv
            );
            return;
        }

        // NGII WMTS 지도 생성 (래퍼)
        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 3, // 초기 줌 (대략 한국 전체)
        });

        // 내부 OpenLayers ol.Map 얻기
        if (typeof $scope.map1._getMap === 'function') {
            olMap = $scope.map1._getMap();
        } else {
            console.warn(
                '_getMap 함수를 찾을 수 없습니다. NGII 스크립트 버전 확인 필요.'
            );
            olMap = null;
        }

        // 실제 ol.Map 에 벡터 레이어 추가
        if (olMap && typeof olMap.addLayer === 'function') {
            olMap.addLayer(stopLayer);
            olMap.addLayer(busLayer);
            console.log('벡터 레이어(정류장/버스)를 ol.Map 에 추가 완료.');
        } else {
            console.warn(
                'ol.Map 인스턴스 또는 addLayer 를 찾지 못했습니다. 마커는 표시되지 않을 수 있습니다.'
            );
        }

        console.log('NGII map 초기화 완료:', $scope.map1, olMap);
    };

    // Angular 뷰 로딩 후 지도 초기화
    $timeout($scope.initMap, 0);

    // -------------------------
    // 정류장 마커 관련 함수
    // -------------------------

    // 정류장 마커 전체 삭제
    function clearStopMarkers() {
        stopSource.clear();
    }

    // 정류장 마커 1개 추가
    function addStopMarker(lat, lon, title) {
        // 지도 인스턴스가 없으면 스킵
        if (!olMap) return;
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            // TAGO(WGS84, EPSG:4326) → NGII(EPSG:5179) 좌표 변환
            var xy5179 = ol.proj.transform(
                [lon, lat], // [경도, 위도]
                'EPSG:4326', // 입력 좌표계
                'EPSG:5179' // 출력 좌표계
            );

            // 포인트 Feature 생성
            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
                name: title || '',
            });

            // 정류장 스타일 (빨간 점)
            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 4,
                        fill: new ol.style.Fill({ color: '#ff0000' }),
                        stroke: new ol.style.Stroke({
                            color: '#ffffff',
                            width: 1,
                        }),
                    }),
                })
            );

            // 벡터 소스에 추가
            stopSource.addFeature(feature);
        } catch (e) {
            console.warn('정류장 마커 생성/좌표 변환 오류:', e);
        }
    }

    // ⭐ 정류장 전체 영역으로 지도 뷰 맞추기
    function fitMapToStops() {
        // 지도/정류장 소스 체크
        if (!olMap) return;

        var extent = stopSource.getExtent(); // [minX, minY, maxX, maxY]

        // extent 값이 유효한지 체크
        if (
            !extent ||
            !isFinite(extent[0]) ||
            !isFinite(extent[1]) ||
            !isFinite(extent[2]) ||
            !isFinite(extent[3])
        ) {
            return;
        }

        // 지도 뷰를 정류장 영역에 맞추기
        var view = olMap.getView();
        if (!view) return;

        view.fit(extent, {
            padding: [50, 50, 50, 50], // 상하좌우 여백
            maxZoom: 14, // 너무 깊게 줌 안 되도록 상한
            duration: 500, // 애니메이션 시간(ms)
        });
    }

    // 정류장 배열을 지도에 표시
    function drawStopsOnMap(stops) {
        // 기존 정류장 마커 제거
        clearStopMarkers();

        // 데이터가 없으면 리턴
        if (!stops || !stops.length) return;

        // 각 정류장에 대해 마커 추가
        stops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                addStopMarker(
                    lat,
                    lon,
                    s.nodenm || s.stationName || '' // 정류장 이름
                );
            }
        });

        // ⭐ 모든 정류장 마커가 추가된 뒤, 그 영역으로 지도 이동
        fitMapToStops();
    }

    // -------------------------
    // 버스 위치 마커 관련 함수
    // -------------------------

    // 버스 마커 전체 삭제
    function clearBusMarkers() {
        busSource.clear();
    }

    // 버스 마커 1개 추가
    function addBusMarker(lat, lon, title) {
        if (!olMap) return;
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xy5179 = ol.proj.transform(
                [lon, lat],
                'EPSG:4326',
                'EPSG:5179'
            );

            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
                name: title || '',
            });

            // 버스 스타일 (파란 점)
            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 5,
                        fill: new ol.style.Fill({ color: '#0000ff' }),
                        stroke: new ol.style.Stroke({
                            color: '#ffffff',
                            width: 1,
                        }),
                    }),
                })
            );

            busSource.addFeature(feature);
        } catch (e) {
            console.warn('버스 마커 생성/좌표 변환 오류:', e);
        }
    }

    // 버스 배열을 지도에 표시
    function drawBusLocationsOnMap(busItems) {
        // 기존 버스 마커 제거
        clearBusMarkers();

        if (!busItems || !busItems.length) return;

        busItems.forEach(function (b) {
            var lat = parseFloat(b.gpslati);
            var lon = parseFloat(b.gpslong);

            if (!isNaN(lat) && !isNaN(lon)) {
                var label =
                    (b.vehicleno || '') + ' / ' + (b.routenm || '');
                addBusMarker(lat, lon, label.trim());
            }
        });
        // ※ 여기는 굳이 fit 하지 않음 (정류장 기준으로 이미 맞춰져 있음)
    }

    // -------------------------
    // 자동 갱신(폴링) 관련
    // -------------------------
    function cancelAutoRefresh() {
        if (autoRefreshPromise) {
            $interval.cancel(autoRefreshPromise);
            autoRefreshPromise = null;
        }
        $scope.isAutoRefreshOn = false;
    }

    function startAutoRefresh() {
        // 기존 타이머 정리
        cancelAutoRefresh();

        if (!$scope.currentRouteId) return;

        // 10초마다 버스 위치만 재조회
        autoRefreshPromise = $interval(function () {
            $scope.fetchBusLocations();
        }, 10000);

        $scope.isAutoRefreshOn = true;
    }

    // 컨트롤러 destroy 시 타이머 정리
    $scope.$on('$destroy', function () {
        cancelAutoRefresh();
    });

    // 자동 새로고침 버튼: ON
    $scope.enableAutoRefresh = function () {
        if (!$scope.currentRouteId) {
            alert('먼저 버스 번호를 검색해서 노선을 선택하세요.');
            return;
        }
        startAutoRefresh();
    };

    // 자동 새로고침 버튼: OFF
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

        $http
            .get('/api/bus/routes', {
                params: { routeNo: routeNo },
            })
            .then(function (res) {
                // JSON 디버그용 문자열 저장
                if (angular.isString(res.data)) {
                    $scope.routeResultJson = res.data;
                } else {
                    $scope.routeResultJson = JSON.stringify(
                        res.data,
                        null,
                        2
                    );
                }

                // 응답 객체 파싱
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    console.warn('노선 응답 구조가 예상과 다름:', data);
                    alert(
                        '노선 정보를 찾을 수 없습니다. (응답 구조 확인 필요)'
                    );
                    return;
                }

                var items =
                    data.response.body.items &&
                    data.response.body.items.item;
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

                // 현재 노선 ID 저장
                $scope.currentRouteId = routeId;

                // 정류장 + 버스 위치 조회
                $scope.fetchRouteStops(routeId);
                $scope.fetchBusLocations();

                // 검색과 동시에 자동 새로고침 시작
                startAutoRefresh();
            })
            .catch(function (err) {
                console.error('노선 조회 실패:', err);

                var msg = err && err.data
                    ? (angular.isString(err.data)
                        ? err.data
                        : JSON.stringify(err.data, null, 2))
                    : err.status + ' ' + err.statusText;
                $scope.routeResultJson = 'ERROR: ' + msg;

                alert('노선 정보를 가져오지 못했습니다.');
            });
    };

    // =========================
    // 2단계: 노선ID → 정류장 목록 조회
    // =========================
    $scope.fetchRouteStops = function (routeId) {
        if (!routeId) return;

        $http
            .get('/api/bus/route-stops', {
                params: { routeId: routeId },
            })
            .then(function (res) {
                // JSON 디버그용
                if (angular.isString(res.data)) {
                    $scope.stopsResultJson = res.data;
                } else {
                    $scope.stopsResultJson = JSON.stringify(
                        res.data,
                        null,
                        2
                    );
                }

                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    console.warn('정류장 응답 구조가 예상과 다름:', data);
                    alert('정류장 정보를 찾을 수 없습니다.');
                    return;
                }

                var items =
                    data.response.body.items &&
                    data.response.body.items.item;
                if (!items) {
                    alert('정류장 목록이 비어 있습니다.');
                    return;
                }

                // 배열 형태로 통일
                var stopsArray = angular.isArray(items) ? items : [items];
                $scope.stops = stopsArray;

                // 정류장 마커 찍기 + 그 영역으로 지도 이동
                drawStopsOnMap(stopsArray);
            })
            .catch(function (err) {
                console.error('정류장 목록 조회 실패:', err);

                var msg = err && err.data
                    ? (angular.isString(err.data)
                        ? err.data
                        : JSON.stringify(err.data, null, 2))
                    : err.status + ' ' + err.statusText;
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

        // 지도 내부 로딩 시작
        $scope.isMapLoading = true;

        $http
            .get('/api/bus/locations', {
                params: {
                    routeId: $scope.currentRouteId,
                    pageNo: 1,
                    numOfRows: 100,
                },
            })
            .then(function (res) {
                // JSON 디버그용
                if (angular.isString(res.data)) {
                    $scope.locationResultJson = res.data;
                } else {
                    $scope.locationResultJson = JSON.stringify(
                        res.data,
                        null,
                        2
                    );
                }

                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    console.warn(
                        '버스 위치 응답 구조가 예상과 다름:',
                        data
                    );
                    clearBusMarkers();
                    return;
                }

                var items =
                    data.response.body.items &&
                    data.response.body.items.item;
                if (!items) {
                    console.warn('버스 위치 목록이 비어 있음');
                    clearBusMarkers();
                    return;
                }

                var busArray = angular.isArray(items)
                    ? items
                    : [items];

                // 버스 위치 마커 찍기
                drawBusLocationsOnMap(busArray);
            })
            .catch(function (err) {
                console.error('버스 위치 조회 실패:', err);

                var msg = err && err.data
                    ? (angular.isString(err.data)
                        ? err.data
                        : JSON.stringify(err.data, null, 2))
                    : err.status + ' ' + err.statusText;
                $scope.locationResultJson = 'ERROR: ' + msg;
            })
            .finally(function () {
                // 지도 내부 로딩 종료
                $scope.isMapLoading = false;
            });
    };
});

// 수정됨 끝
