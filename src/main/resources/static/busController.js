// 수정됨: 3번(노선 라인 표시) 추가 - 버스(노선) 검색 시 정류장 좌표를 routeseq 기준으로 이어서 라인 표시 + 지도 fit
//       - 정류장 검색 모드에서는 라인 제거(노선이 특정되지 않으므로)

/*
 * TAGO 좌표 → NGII 변환 + 정류장/버스 마커 표시 + 자동 새로고침
 * + 정류장 영역으로 지도 자동 이동 + 대표 버스 유지
 * + 현재 정류장 기준 도착 예정 버스 목록(arrivalList) 조회/표시
 * + 버스/정류장 통합 검색 + 정류장 이름 검색 + 정류장 클릭 시 도착정보 조회
 * + [정류장 검색 모드] 정류장 선택 시 도착 예정 버스들의 노선(routeId) 기준으로 버스 위치 전부 표시
 * + [폴링] 정류장/버스 둘 다 “깜빡임 최소화” (목록/마커를 미리 비우지 않고 준비 후 교체)
 */

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

    // 통합 검색 타입 및 공통 검색어
    // 'route' = 버스(노선), 'stop' = 정류장
    $scope.searchType    = 'route';
    $scope.searchKeyword = '';

    // 기존 버스 검색용(노선 API에 넘길 값)
    $scope.searchTerm = '';

    // NGII 지도 래퍼 객체 (ngii_wmts.map 인스턴스)
    $scope.map1 = null;

    // 실제 OpenLayers 지도 객체
    var olMap = null;

    // JSON 디버그용 바인딩
    $scope.routeResultJson    = ''; // 노선 JSON
    $scope.stopsResultJson    = ''; // 정류장 JSON
    $scope.locationResultJson = ''; // 버스 위치 JSON;

    // 선택된 노선 ID
    $scope.currentRouteId = null;

    // 정류장 배열 (TAGO 응답 파싱 결과)
    $scope.stops = [];

    // 정류장 목록에서 사용자가 클릭한 정류장
    $scope.selectedStop = null;

    // 자동 새로고침 타이머 핸들
    var autoRefreshPromise = null;

    // 자동 새로고침 상태
    $scope.isAutoRefreshOn = false;

    // 지도 내부 로딩 플래그 (지도 중앙 로딩 오버레이에서 사용)
    $scope.isMapLoading = false;

    // 대표 버스(랜덤 1대, 가능하면 계속 유지)
    $scope.representativeBus = null;

    // 대표 버스 기준 이전/현재/다음 정류장
    $scope.prevStop    = null;
    $scope.currentStop = null;
    $scope.nextStop    = null;

    // 현재 정류장 기준 도착 예정 버스 목록
    $scope.arrivalList = [];

    // [정류장 모드] 버스 마커 업데이트용 리퀘스트 번호 (race condition 방지)
    var lastArrivalDrawRequestId = 0;

    // -------------------------
    // OpenLayers 벡터 레이어 준비 (정류장/버스)
    // -------------------------
    var stopSource = new ol.source.Vector();
    var stopLayer  = new ol.layer.Vector({
        source: stopSource
    });

    var busSource = new ol.source.Vector();
    var busLayer  = new ol.layer.Vector({
        source: busSource
    });

    // -------------------------
    // [추가] 노선 라인 레이어 준비 (3번)
    //  - 버스(노선) 검색 시, 정류장들을 routeseq 기준으로 이어서 LineString 표시
    // -------------------------
    var routeLineSource = new ol.source.Vector();
    var routeLineLayer  = new ol.layer.Vector({
        source: routeLineSource,
        style: new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: '#0066ff', // 라인 색상
                width: 4          // 라인 두께
            })
        })
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
        var mapDiv = document.getElementById('map1');

        if (!window.ngii_wmts || !mapDiv) {
            console.error(
                'NGII 지도 스크립트 또는 #map1 요소를 찾을 수 없습니다.',
                window.ngii_wmts,
                mapDiv
            );
            return;
        }

        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 3 // 초기 한국 전체
        });

        if (typeof $scope.map1._getMap === 'function') {
            olMap = $scope.map1._getMap();
        } else {
            console.warn('_getMap 함수를 찾을 수 없습니다. NGII 스크립트 버전 확인 필요.');
            olMap = null;
        }

        if (olMap && typeof olMap.addLayer === 'function') {
            // ✅ 레이어 순서 중요: 라인 → 정류장 → 버스 (라인 위에 마커가 보이도록)
            olMap.addLayer(routeLineLayer); // [추가] 노선 라인
            olMap.addLayer(stopLayer);      // 정류장 마커
            olMap.addLayer(busLayer);       // 버스 마커

            console.log('벡터 레이어(노선 라인/정류장/버스)를 ol.Map 에 추가 완료.');
        } else {
            console.warn('ol.Map 인스턴스 또는 addLayer 를 찾지 못했습니다. 마커는 표시되지 않을 수 있습니다.');
        }

        console.log('NGII map 초기화 완료:', $scope.map1, olMap);
    };

    $timeout($scope.initMap, 0);

    // -------------------------
    // [추가] 노선 라인 관련 함수 (3번)
    // -------------------------
    function clearRouteLine() {
        routeLineSource.clear();
    }

    function drawRouteLineFromStops(stops) {
        // 노선 검색 모드에서만 사용 (정류장 검색 모드에서는 노선이 특정되지 않음)
        if (!$scope.currentRouteId) {
            clearRouteLine();
            return;
        }

        clearRouteLine();

        if (!olMap) return;
        if (!stops || stops.length < 2) return;

        // routeseq 기준 정렬 (노선의 실제 순서)
        var sortedStops = stops.slice().sort(function (a, b) {
            var sa = parseInt(a.routeseq || a.routeSeq || 0, 10);
            var sb = parseInt(b.routeseq || b.routeSeq || 0, 10);
            return sa - sb;
        });

        // EPSG:5179 좌표 배열 생성
        var coordinates = [];
        sortedStops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                var xy5179 = ol.proj.transform(
                    [lon, lat],
                    'EPSG:4326',
                    'EPSG:5179'
                );
                coordinates.push(xy5179);
            }
        });

        if (coordinates.length < 2) return;

        // 라인 피처 생성
        var lineFeature = new ol.Feature({
            geometry: new ol.geom.LineString(coordinates)
        });

        // 라인 레이어에 추가
        routeLineSource.addFeature(lineFeature);

        // 라인 기준으로 지도 범위 맞추기 (정류장 fit과 유사)
        var extent = routeLineSource.getExtent();
        if (extent && isFinite(extent[0]) && isFinite(extent[1]) && isFinite(extent[2]) && isFinite(extent[3])) {
            var view = olMap.getView();
            if (!view) return;

            view.fit(extent, {
                padding: [60, 60, 60, 60],
                maxZoom: 14,
                duration: 500
            });
        }
    }

    // -------------------------
    // 정류장 마커 관련 함수
    // -------------------------
    function clearStopMarkers() {
        // 완전 제거 시에만 사용 (평소에는 새 source로 교체 방식 사용)
        var newSrc = new ol.source.Vector();
        stopLayer.setSource(newSrc);
        stopSource = newSrc;
    }

    function addStopMarkerToSource(targetSource, lat, lon, title) {
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
                name: title || ''
            });

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 4,
                        fill: new ol.style.Fill({ color: '#ff0000' }),
                        stroke: new ol.style.Stroke({
                            color: '#ffffff',
                            width: 1
                        })
                    })
                })
            );

            targetSource.addFeature(feature);
        } catch (e) {
            console.warn('정류장 마커 생성/좌표 변환 오류:', e);
        }
    }

    function fitMapToStops() {
        if (!olMap) return;

        var extent = stopSource.getExtent();

        if (
            !extent ||
            !isFinite(extent[0]) ||
            !isFinite(extent[1]) ||
            !isFinite(extent[2]) ||
            !isFinite(extent[3])
        ) {
            return;
        }

        var view = olMap.getView();
        if (!view) return;

        view.fit(extent, {
            padding: [50, 50, 50, 50],
            maxZoom: 14,
            duration: 500
        });
    }

    function drawStopsOnMap(stops) {
        if (!stops || !stops.length) {
            clearStopMarkers();
            return;
        }

        // flicker 방지: 새 source에 그린 뒤 교체
        var newSrc = new ol.source.Vector();

        stops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                addStopMarkerToSource(
                    newSrc,
                    lat,
                    lon,
                    s.nodenm || s.stationName || ''
                );
            }
        });

        stopLayer.setSource(newSrc);
        stopSource = newSrc;

        fitMapToStops();
    }

    // -------------------------
    // 버스 마커 관련 함수
    // -------------------------
    function clearBusMarkers() {
        // 완전 제거 시에만 사용 (평소에는 새 source로 교체)
        var newSrc = new ol.source.Vector();
        busLayer.setSource(newSrc);
        busSource = newSrc;
    }

    function addBusMarkerToSource(targetSource, lat, lon, title, isRepresentative) {
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
                name: title || ''
            });

            var fillColor   = isRepresentative ? '#ffd400' : '#0000ff';
            var radiusValue = isRepresentative ? 7 : 5;

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: radiusValue,
                        fill: new ol.style.Fill({ color: fillColor }),
                        stroke: new ol.style.Stroke({
                            color: '#ffffff',
                            width: 1
                        })
                    })
                })
            );

            targetSource.addFeature(feature);
        } catch (e) {
            console.warn('버스 마커 생성/좌표 변환 오류:', e);
        }
    }

    function drawBusLocationsOnMap(busItems) {
        // 노선 모드에서 버스 마커: 새 source로 교체 (flicker 방지)
        if (!busItems || !busItems.length) {
            clearBusMarkers();
            return;
        }

        var newSrc = new ol.source.Vector();
        var rep = $scope.representativeBus;

        busItems.forEach(function (b) {
            var lat = parseFloat(b.gpslati);
            var lon = parseFloat(b.gpslong);

            if (!isNaN(lat) && !isNaN(lon)) {
                var label = (b.vehicleno || '') + ' / ' + (b.routenm || '');

                var isRepresentative = false;
                if (rep && rep.vehicleno && b.vehicleno) {
                    isRepresentative = (rep.vehicleno === b.vehicleno);
                }

                addBusMarkerToSource(newSrc, lat, lon, label.trim(), isRepresentative);
            }
        });

        busLayer.setSource(newSrc);
        busSource = newSrc;
    }

    // -------------------------
    // 대표 버스의 이전/현재/다음 정류장 계산
    // -------------------------
    function computePrevCurrentNextForBus(bus, stops) {
        var result = {
            prev: null,
            current: null,
            next: null
        };

        if (!bus || !stops || !stops.length) {
            return result;
        }

        var currentIndex = -1;
        var busNodeId    = bus.nodeid || bus.nodeId || null;
        var busSeq       = bus.routeseq || bus.routeSeq || null;

        // 1순위: nodeid 매칭
        if (busNodeId) {
            for (var i = 0; i < stops.length; i++) {
                var s = stops[i];
                if ((s.nodeid || s.nodeId) === busNodeId) {
                    currentIndex = i;
                    break;
                }
            }
        }

        // 2순위: routeseq 매칭
        if (currentIndex === -1 && busSeq != null) {
            var busSeqNum = parseInt(busSeq, 10);
            if (!isNaN(busSeqNum)) {
                for (var j = 0; j < stops.length; j++) {
                    var st = stops[j];
                    var stopSeq = parseInt(st.routeseq || st.routeSeq, 10);
                    if (!isNaN(stopSeq) && stopSeq === busSeqNum) {
                        currentIndex = j;
                        break;
                    }
                }
            }
        }

        if (currentIndex === -1) {
            return result;
        }

        result.current = stops[currentIndex];
        if (currentIndex > 0) {
            result.prev = stops[currentIndex - 1];
        }
        if (currentIndex < stops.length - 1) {
            result.next = stops[currentIndex + 1];
        }

        return result;
    }

    // -------------------------
    // [정류장 모드] 도착정보 기반 버스 위치 전부 표시 (깜빡임 최소화)
    // -------------------------
    function drawBusesForArrivalRoutes(arrivals) {
        // 정류장 검색 모드가 아니면 여기서는 아무것도 하지 않음
        if ($scope.currentRouteId) {
            return;
        }

        // 도착정보가 없으면 마커 제거
        if (!arrivals || !arrivals.length) {
            clearBusMarkers();
            return;
        }

        // 도착정보에 포함된 routeId(또는 routeid)를 전부 모아서 중복 제거
        var routeIdMap = {};
        arrivals.forEach(function (a) {
            var rid = a.routeid || a.routeId || a.route_id;
            if (rid) {
                routeIdMap[rid] = true;
            }
        });

        var routeIds = Object.keys(routeIdMap);
        if (!routeIds.length) {
            clearBusMarkers();
            return;
        }

        // flicker 방지:
        // - routeIds만큼 /locations 병렬 호출
        // - tempSource에만 추가
        // - 전부 끝나면 한 번에 교체
        lastArrivalDrawRequestId++;
        var myReqId = lastArrivalDrawRequestId;

        var pending = routeIds.length;
        var tempSource = new ol.source.Vector();

        routeIds.forEach(function (rid) {
            $http.get('/api/bus/locations', {
                params: {
                    routeId:  rid,
                    pageNo:   1,
                    numOfRows: 100
                }
            }).then(function (res) {
                if (myReqId !== lastArrivalDrawRequestId) {
                    return;
                }

                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    console.warn('정류장 모드 버스 위치 응답 구조가 예상과 다름(routeId=' + rid + '):', data);
                    return;
                }

                var items = data.response.body.items && data.response.body.items.item;
                if (!items) {
                    return;
                }

                var busArray = angular.isArray(items) ? items : [items];

                busArray.forEach(function (b) {
                    var lat = parseFloat(b.gpslati);
                    var lon = parseFloat(b.gpslong);
                    if (isNaN(lat) || isNaN(lon)) {
                        return;
                    }

                    var label = (b.vehicleno || '') + ' / ' + (b.routenm || '');
                    addBusMarkerToSource(tempSource, lat, lon, label.trim(), false);
                });
            }).catch(function (err) {
                console.error('정류장 모드 버스 위치 조회 실패(routeId=' + rid + '):', err);
            }).finally(function () {
                if (myReqId !== lastArrivalDrawRequestId) {
                    return;
                }

                pending--;
                if (pending === 0) {
                    busLayer.setSource(tempSource);
                    busSource = tempSource;
                }
            });
        });
    }

    // -------------------------
    // 현재 정류장 기준 도착 예정 버스 조회 (깜빡임 최소화)
    // -------------------------
    function fetchArrivalsForCurrentStop() {
        if (!$scope.currentStop) {
            return;
        }

        var nodeId = $scope.currentStop.nodeid || $scope.currentStop.nodeId;
        if (!nodeId) {
            return;
        }

        var previousArrivalList = $scope.arrivalList || [];

        $http.get('/api/bus/arrivals', {
            params: {
                nodeId:    nodeId,
                numOfRows: 20
            }
        }).then(function (res) {
            var data = parseMaybeJson(res.data);
            if (!data || !data.response || !data.response.body) {
                console.warn('도착정보 응답 구조가 예상과 다름:', data);
                $scope.arrivalList = previousArrivalList;
                return;
            }

            var items = data.response.body.items && data.response.body.items.item;
            if (!items) {
                $scope.arrivalList = [];
                clearBusMarkers();
                return;
            }

            var list = angular.isArray(items) ? items : [items];

            var mapped = list.map(function (a) {
                var remainStops = (a.arrprevstationcnt != null)
                    ? parseInt(a.arrprevstationcnt, 10)
                    : null;

                var sec = (a.arrtime != null) ? parseInt(a.arrtime, 10) : null;
                var minutes = null;
                if (!isNaN(sec) && sec != null) {
                    minutes = Math.round(sec / 60.0);
                }

                return angular.extend({}, a, {
                    remainStops: isNaN(remainStops) ? null : remainStops,
                    remainMinutes: minutes
                });
            });

            $scope.arrivalList = mapped;

            // 정류장 검색 모드라면, 이 도착정보 기준으로 버스 위치들 갱신
            drawBusesForArrivalRoutes($scope.arrivalList);

        }).catch(function (err) {
            console.error('도착 예정 버스 조회 실패:', err);
            $scope.arrivalList = previousArrivalList;
        });
    }

    // -------------------------
    // 정류장 목록 클릭 시: 기준 정류장 설정 + 도착정보 조회
    // -------------------------
    $scope.selectStop = function (stop) {
        if (!stop) return;

        $scope.selectedStop = stop;
        $scope.currentStop  = stop;

        fetchArrivalsForCurrentStop();
    };

    // -------------------------
    // 자동 갱신(폴링) 관련
    //   - 노선 모드: 버스 위치(fetchBusLocations) 폴링
    //   - 정류장 모드: 도착정보(fetchArrivalsForCurrentStop) 폴링
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

        if ($scope.currentRouteId) {
            autoRefreshPromise = $interval(function () {
                $scope.fetchBusLocations();
            }, 10000);
            $scope.isAutoRefreshOn = true;

        } else if ($scope.selectedStop) {
            autoRefreshPromise = $interval(function () {
                fetchArrivalsForCurrentStop();
            }, 10000);
            $scope.isAutoRefreshOn = true;
        }
    }

    $scope.$on('$destroy', function () {
        cancelAutoRefresh();
    });

    $scope.enableAutoRefresh = function () {
        if ($scope.currentRouteId || $scope.selectedStop) {
            startAutoRefresh();
        } else {
            alert('먼저 버스 번호를 검색하거나 정류장을 선택하세요.');
        }
    };

    $scope.disableAutoRefresh = function () {
        cancelAutoRefresh();
    };

    // =========================
    // 공통 검색: 버스/정류장 모드 분기
    // =========================
    $scope.doSearch = function () {
        if (!$scope.searchKeyword) {
            alert('검색어를 입력하세요.');
            return;
        }

        if ($scope.searchType === 'route') {
            $scope.searchTerm = $scope.searchKeyword;
            $scope.searchBus();
        } else if ($scope.searchType === 'stop') {
            $scope.searchStops();
        } else {
            $scope.searchTerm = $scope.searchKeyword;
            $scope.searchBus();
        }
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

        cancelAutoRefresh();

        $http.get('/api/bus/routes', {
            params: { routeNo: routeNo }
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

            $scope.currentRouteId    = routeId;
            $scope.representativeBus = null;
            $scope.prevStop          = null;
            $scope.currentStop       = null;
            $scope.nextStop          = null;
            $scope.arrivalList       = [];
            $scope.selectedStop      = null;

            $scope.fetchRouteStops(routeId);
            $scope.fetchBusLocations();

            startAutoRefresh();
        }).catch(function (err) {
            console.error('노선 조회 실패:', err);

            var msg = err && err.data
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.routeResultJson = 'ERROR: ' + msg;

            alert('노선 정보를 가져오지 못했습니다.');
        });
    };

    // =========================
    // 2단계: 노선ID → 정류장 목록 조회
    // =========================
    $scope.fetchRouteStops = function (routeId) {
        if (!routeId) return;

        $http.get('/api/bus/route-stops', {
            params: { routeId: routeId }
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
            $scope.stops        = stopsArray;
            $scope.selectedStop = null;

            // 정류장 마커
            drawStopsOnMap(stopsArray);

            // ✅ [추가] 노선 라인 (3번)
            drawRouteLineFromStops(stopsArray);

            // 대표 버스가 있으면 prev/current/next 재계산
            if ($scope.representativeBus) {
                var calc = computePrevCurrentNextForBus($scope.representativeBus, $scope.stops);
                $scope.prevStop    = calc.prev;
                $scope.currentStop = calc.current;
                $scope.nextStop    = calc.next;

                fetchArrivalsForCurrentStop();
            }
        }).catch(function (err) {
            console.error('정류장 목록 조회 실패:', err);

            var msg = err && err.data
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.stopsResultJson = 'ERROR: ' + msg;

            alert('정류장 정보를 가져오지 못했습니다.');
        });
    };

    // =========================
    // 정류장 이름으로 정류장 목록 검색
    // =========================
    $scope.searchStops = function () {
        if (!$scope.searchKeyword) {
            alert('정류장 이름을 입력하세요.');
            return;
        }

        var keyword = $scope.searchKeyword;

        cancelAutoRefresh();

        // 버스 관련 상태 초기화
        $scope.currentRouteId    = null;
        $scope.representativeBus = null;
        $scope.prevStop          = null;
        $scope.currentStop       = null;
        $scope.nextStop          = null;
        $scope.arrivalList       = [];
        $scope.selectedStop      = null;

        // ✅ [추가] 정류장 검색 모드에서는 노선 라인이 의미 없으므로 제거
        clearRouteLine();

        // 버스 마커 제거(정류장만 보이게)
        clearBusMarkers();

        $scope.isMapLoading = true;

        $http.get('/api/bus/stops-by-name', {
            params: {
                nodeName: keyword,
                pageNo:   1,
                numOfRows: 100
            }
        }).then(function (res) {
            if (angular.isString(res.data)) {
                $scope.stopsResultJson = res.data;
            } else {
                $scope.stopsResultJson = JSON.stringify(res.data, null, 2);
            }

            var data = parseMaybeJson(res.data);
            if (!data || !data.response || !data.response.body) {
                console.warn('정류장 검색 응답 구조가 예상과 다름:', data);
                $scope.stops        = [];
                $scope.selectedStop = null;
                return;
            }

            var itemsRoot = data.response.body.items;
            if (!itemsRoot || !itemsRoot.item) {
                $scope.stops        = [];
                $scope.selectedStop = null;
                alert('검색된 정류장이 없습니다.');
                return;
            }

            var items = itemsRoot.item;
            var rawStopsArray = angular.isArray(items) ? items : [items];

            var stopsArray = rawStopsArray.map(function (s) {
                var id =
                    s.nodeid ||
                    s.nodeId ||
                    s.node_id ||
                    s.nodeno ||
                    s.sttnId ||
                    s.stationId;

                return angular.extend({}, s, { nodeid: id });
            });

            $scope.stops        = stopsArray;
            $scope.selectedStop = null;

            // 정류장만 지도에 마커로 표시
            drawStopsOnMap(stopsArray);

        }).catch(function (err) {
            console.error('정류장 검색 실패:', err);

            var msg = err && err.data
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.stopsResultJson = 'ERROR: ' + msg;

            alert('정류장 정보를 가져오지 못했습니다.');
        }).finally(function () {
            $scope.isMapLoading = false;
        });
    };

    // =========================
    // 3단계: 현재 routeId 기준 버스 위치 조회 (폴링 대상)
    // =========================
    $scope.fetchBusLocations = function () {
        if (!$scope.currentRouteId) {
            return;
        }

        $scope.isMapLoading = true;

        $http.get('/api/bus/locations', {
            params: {
                routeId:   $scope.currentRouteId,
                pageNo:    1,
                numOfRows: 100
            }
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
                $scope.representativeBus = null;
                $scope.prevStop    = null;
                $scope.currentStop = null;
                $scope.nextStop    = null;
                $scope.arrivalList = [];
                $scope.selectedStop = null;
                return;
            }

            var items = data.response.body.items && data.response.body.items.item;
            if (!items) {
                console.warn('버스 위치 목록이 비어 있음');
                clearBusMarkers();
                $scope.representativeBus = null;
                $scope.prevStop    = null;
                $scope.currentStop = null;
                $scope.nextStop    = null;
                $scope.arrivalList = [];
                $scope.selectedStop = null;
                return;
            }

            var busArray = angular.isArray(items) ? items : [items];

            // 대표 버스 유지 로직
            var newRepresentative = null;
            var oldRep = $scope.representativeBus;

            // 1) 기존 대표 버스가 여전히 응답에 있으면 그대로 사용
            if (oldRep && oldRep.vehicleno) {
                for (var i = 0; i < busArray.length; i++) {
                    var b = busArray[i];
                    if (b.vehicleno && b.vehicleno === oldRep.vehicleno) {
                        newRepresentative = b;
                        break;
                    }
                }
            }

            // 2) 없으면 그때만 새로 랜덤 뽑기
            if (!newRepresentative && busArray.length > 0) {
                var idx = Math.floor(Math.random() * busArray.length);
                newRepresentative = busArray[idx];
            }

            $scope.representativeBus = newRepresentative || null;

            // 대표 버스 기준 이전/현재/다음 정류장 계산
            if ($scope.representativeBus && $scope.stops && $scope.stops.length > 0) {
                var calc2 = computePrevCurrentNextForBus($scope.representativeBus, $scope.stops);
                $scope.prevStop    = calc2.prev;
                $scope.currentStop = calc2.current;
                $scope.nextStop    = calc2.next;

                fetchArrivalsForCurrentStop();
            } else {
                $scope.prevStop    = null;
                $scope.currentStop = null;
                $scope.nextStop    = null;
                $scope.arrivalList = [];
                $scope.selectedStop = null;
            }

            // 버스 위치 마커 찍기 (대표 버스 노란색 강조)
            drawBusLocationsOnMap(busArray);
        }).catch(function (err) {
            console.error('버스 위치 조회 실패:', err);

            var msg = err && err.data
                ? (angular.isString(err.data) ? err.data : JSON.stringify(err.data, null, 2))
                : (err.status + ' ' + err.statusText);
            $scope.locationResultJson = 'ERROR: ' + msg;

            $scope.representativeBus = null;
            $scope.prevStop    = null;
            $scope.currentStop = null;
            $scope.nextStop    = null;
            $scope.arrivalList = [];
            $scope.selectedStop = null;
        }).finally(function () {
            $scope.isMapLoading = false;
        });
    };
});

// 수정됨 끝
