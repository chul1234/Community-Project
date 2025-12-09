// 수정됨: 지도 내부 전용 로딩 플래그(isMapLoading) 추가

// EPSG:5179 정의 + proj4 등록 (기존 코드 그대로라고 가정)
proj4.defs(
    'EPSG:5179',
    '+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 ' +
    '+x_0=1000000 +y_0=2000000 +ellps=GRS80 +units=m +no_defs'
);
ol.proj.proj4.register(proj4);

app.controller('BusController', function ($scope, $http, $timeout, $interval) {

    const CITY_CODE = '25';

    $scope.searchTerm = '';

    $scope.map1 = null;   // NGII 래퍼
    var olMap = null;      // 실제 OpenLayers 지도

    $scope.routeResultJson    = '';
    $scope.stopsResultJson    = '';
    $scope.locationResultJson = '';

    $scope.currentRouteId = null;
    $scope.stops = [];

    var autoRefreshPromise = null;
    $scope.isAutoRefreshOn = false;

    // ⭐ 지도 전용 로딩 플래그 (지도 안 ●●● 표시용)
    $scope.isMapLoading = false;

    // 벡터 레이어 준비
    var stopSource = new ol.source.Vector();
    var stopLayer  = new ol.layer.Vector({ source: stopSource });

    var busSource = new ol.source.Vector();
    var busLayer  = new ol.layer.Vector({ source: busSource });

    // JSON 파서
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

    // 지도 초기화
    $scope.initMap = function () {
        var mapDiv = document.getElementById('map1');

        if (!window.ngii_wmts || !mapDiv) {
            console.error('NGII 지도 스크립트 또는 #map1 요소를 찾을 수 없습니다.', window.ngii_wmts, mapDiv);
            return;
        }

        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 3,
        });

        if (typeof $scope.map1._getMap === 'function') {
            olMap = $scope.map1._getMap();
        } else {
            console.warn('_getMap 함수를 찾을 수 없습니다. NGII 스크립트 버전 확인 필요.');
            olMap = null;
        }

        if (olMap && typeof olMap.addLayer === 'function') {
            olMap.addLayer(stopLayer);
            olMap.addLayer(busLayer);
            console.log('벡터 레이어(정류장/버스)를 ol.Map 에 추가 완료.');
        } else {
            console.warn('ol.Map 인스턴스 또는 addLayer 를 찾지 못했습니다. 마커는 표시되지 않을 수 있습니다.');
        }

        console.log('NGII map 초기화 완료:', $scope.map1, olMap);
    };

    $timeout($scope.initMap, 0);

    // 정류장 마커
    function clearStopMarkers() {
        stopSource.clear();
    }

    function addStopMarker(lat, lon, title) {
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

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 4,
                        fill: new ol.style.Fill({ color: '#ff0000' }),
                        stroke: new ol.style.Stroke({ color: '#ffffff', width: 1 }),
                    }),
                })
            );

            stopSource.addFeature(feature);
        } catch (e) {
            console.warn('정류장 마커 생성/좌표 변환 오류:', e);
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

    // 버스 마커
    function clearBusMarkers() {
        busSource.clear();
    }

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

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 5,
                        fill: new ol.style.Fill({ color: '#0000ff' }),
                        stroke: new ol.style.Stroke({ color: '#ffffff', width: 1 }),
                    }),
                })
            );

            busSource.addFeature(feature);
        } catch (e) {
            console.warn('버스 마커 생성/좌표 변환 오류:', e);
        }
    }

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

    // 자동 갱신 (버스 위치)
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
        }, 10000); // 10초

        $scope.isAutoRefreshOn = true;
    }

    $scope.$on('$destroy', function () {
        cancelAutoRefresh();
    });

    // 버튼용 공개 함수
    $scope.enableAutoRefresh = function () {
        if (!$scope.currentRouteId) {
            alert('먼저 버스 번호를 검색해서 노선을 선택하세요.');
            return;
        }
        startAutoRefresh();
    };

    $scope.disableAutoRefresh = function () {
        cancelAutoRefresh();
    };

    // 1단계: 노선 검색
    $scope.searchBus = function () {
        if (!$scope.searchTerm) {
            alert('버스 번호를 입력하세요.');
            return;
        }

        var routeNo = $scope.searchTerm;

        $http.get('/api/bus/routes', {
            params: { routeNo: routeNo },
        }).then(function (res) {
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

            var first   = angular.isArray(items) ? items[0] : items;
            var routeId = first.routeid || first.routeId;
            if (!routeId) {
                alert('응답에서 routeId 를 찾을 수 없습니다.');
                return;
            }

            $scope.currentRouteId = routeId;

            // 정류장 + 버스 위치 조회
            $scope.fetchRouteStops(routeId);
            $scope.fetchBusLocations();

            // 검색과 동시에 자동 새로고침 시작
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

    // 2단계: 정류장 목록
    $scope.fetchRouteStops = function (routeId) {
        if (!routeId) return;

        $http.get('/api/bus/route-stops', {
            params: { routeId: routeId },
        }).then(function (res) {
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

    // 3단계: 버스 위치
    $scope.fetchBusLocations = function () {
        if (!$scope.currentRouteId) {
            return;
        }

        // ⭐ 지도 전용 로딩 ON
        $scope.isMapLoading = true;

        $http.get('/api/bus/locations', {
            params: {
                routeId: $scope.currentRouteId,
                pageNo: 1,
                numOfRows: 100,
            },
        }).then(function (res) {
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

            drawBusLocationsOnMap(busArray);
        }).catch(function (err) {
            console.error('버스 위치 조회 실패:', err);

            var msg = (err && err.data)
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.locationResultJson = 'ERROR: ' + msg;
        }).finally(function () {
            // ⭐ 무조건 지도 로딩 OFF
            $scope.isMapLoading = false;
        });
    };

});

// 수정됨 끝
