// =========================================================
// [최종 수정] busController.js
// 수정 사항: 최단 경로 화살표 회전 각도 오류 수정 (rotation: -angle)
// =========================================================

// 좌표계 정의 (UTM-K, GRS80)
proj4.defs(
    'EPSG:5179', // 좌표계 ID
    '+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 ' + // 투영법 설정
        '+x_0=1000000 +y_0=2000000 +ellps=GRS80 +units=m +no_defs' // 원점 및 타원체 설정
);
ol.proj.proj4.register(proj4); // OpenLayers에 좌표계 등록

// AngularJS 컨트롤러 정의
app.controller('BusController', function ($scope, $http, $timeout, $interval) {
    const CITY_CODE = '25'; // 대전 도시코드

    $scope.searchType = 'route'; // 검색 타입 (기본: 노선)
    $scope.searchKeyword = ''; // 검색어 입력값
    $scope.searchTerm = ''; // 실제 검색어

    $scope.map1 = null; // NGII 지도 객체
    var olMap = null; // OpenLayers 지도 객체

    $scope.routeResultJson = ''; // 노선 검색 결과 JSON
    $scope.stopsResultJson = ''; // 정류장 검색 결과 JSON
    $scope.locationResultJson = ''; // 버스 위치 결과 JSON

    $scope.currentRouteId = null; // 현재 선택된 노선 ID

    $scope.stops = []; // 정류장 목록 배열
    $scope.selectedStop = null; // 선택된 정류장 객체

    var autoRefreshPromise = null; // 자동 새로고침 Promise
    $scope.isAutoRefreshOn = false; // 자동 새로고침 상태 플래그

    $scope.isMapLoading = false; // 지도 로딩 상태 플래그

    $scope.representativeBus = null; // 대표 버스 객체

    $scope.prevStop = null; // 이전 정류장
    $scope.currentStop = null; // 현재 정류장
    $scope.nextStop = null; // 다음 정류장

    $scope.arrivalList = []; // 도착 예정 버스 목록

    var lastArrivalDrawRequestId = 0; // 도착 정보 그리기 요청 ID (비동기 처리용)

    // 정류장 모드: 버스 클릭 시 임시 노선 ID
    $scope.tempRouteIdFromStop = null;

    // ★ [추가] 경로 탐색용 출발/도착 정류장
    $scope.pathStartStop = null;
    $scope.pathTotalMinutes = null; // 최단 경로 총 소요시간(분)

    $scope.pathEndStop = null;

    // =========================================================
    // [핵심] 지도 실제 Projection 코드 기반 좌표 변환 유틸
    // =========================================================
    var MAP_PROJ_CODE = null; // 예: 'EPSG:5179', 'EPSG:3857' 등

    function detectMapProjectionCode() {
        if (!olMap) return null;
        var view = olMap.getView();
        if (!view) return null;
        var proj = view.getProjection();
        if (!proj) return null;
        var code = typeof proj.getCode === 'function' ? proj.getCode() : null;
        return code || null;
    }

    function ensureMapProjCode() {
        if (MAP_PROJ_CODE) return MAP_PROJ_CODE;
        var code = detectMapProjectionCode();
        MAP_PROJ_CODE = code || 'EPSG:5179'; // 최후 fallback
        console.log('[MAP_PROJ_CODE]', MAP_PROJ_CODE);
        return MAP_PROJ_CODE;
    }

    // lon/lat(WGS84) -> 지도 좌표로 변환
    function lonLatToMapXY(lon, lat) {
        var target = ensureMapProjCode();

        // 지도 뷰가 WebMercator(3857)면 fromLonLat이 가장 안전
        if (target === 'EPSG:3857') {
            return ol.proj.fromLonLat([lon, lat]);
        }

        // 그 외(5179 등)는 transform 사용
        return ol.proj.transform([lon, lat], 'EPSG:4326', target);
    }

    // =========================================================
    // [트램] 토글 상태 (HTML 버튼과 바인딩: isTramVisible)
    // =========================================================
    $scope.isTramVisible = false; // 초기엔 숨김

    // =========================================================
    // [트램] 구간별 색상 매핑
    // =========================================================
    var TRAM_SECTION_COLOR_MAP = {
        '1구간': '#AB3937',
        '2구간': '#AB3937',
        '3구간': '#202020',
        '4구간': '#202020',
        '5구간': '#202020',
        '6구간': '#202020',
        '7구간': '#AB3937',
        '8구간': '#AB3937',
        '9구간': '#202020',
        '10구간': '#AB3937',
        '11구간': '#202020',
        '12구간': '#202020',
        '13구간': '#AB3937',
        '14구간': '#202020',
    };

    function getTramSectionColor(sectionName) {
        if (!sectionName) return '#202020';
        return TRAM_SECTION_COLOR_MAP[sectionName] || '#202020';
    }

    var tramLineStyleCache = {};

    function getTramLineStyleByColor(hexColor) {
        var key = String(hexColor || '#202020');
        if (tramLineStyleCache[key]) return tramLineStyleCache[key];

        tramLineStyleCache[key] = new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: key,
                width: 6,
                lineCap: 'round',
                lineJoin: 'round',
            }),
        });

        return tramLineStyleCache[key];
    }

    // =========================================================
    // [디자인] SVG 아이콘 생성 함수
    // =========================================================
    function createSvgIcon(color, type) {
        var svg = '';
        if (type === 'bus') {
            svg =
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">' +
                '<path fill="' +
                color +
                '" d="M48 64C48 28.7 76.7 0 112 0H400c35.3 0 64 28.7 64 64V448c0 35.3-28.7 64-64 64H384c-17.7 0-32-14.3-32-32s14.3-32 32-32h16c8.8 0 16-7.2 16-16V384H96v64c0 8.8 7.2 16 16 16h16c17.7 0 32 14.3 32 32s-14.3 32-32 32H112c-35.3 0-64-28.7-64-64V64zm32 32c0-17.7 14.3-32 32-32H400c17.7 0 32 14.3 32 32v64c0 17.7-14.3 32-32 32H112c-17.7 0-32-14.3-32-32V96zm0 160c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H80zm352 0c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H432z"/>' +
                '</svg>';
        }
        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    }

    // =========================================================
    // [트램] 라인/정거장 레이어
    // =========================================================
    var tramLineSource = new ol.source.Vector();
    var tramLineLayer = new ol.layer.Vector({
        source: tramLineSource,
        zIndex: 4,
    });

    var tramStopSource = new ol.source.Vector();
    var tramStopLayer = new ol.layer.Vector({
        source: tramStopSource,
        zIndex: 8,
    });

    function isIntegerId(idVal) {
        if (idVal == null) return false;
        var n = Number(idVal);
        return Number.isFinite(n) && Math.floor(n) === n;
    }

    function clearTram() {
        tramLineSource.clear();
        tramStopSource.clear();
    }

    function addTramSegmentFeature(coordsMap, sectionName) {
        if (!coordsMap || coordsMap.length < 2) return;

        var color = getTramSectionColor(sectionName);
        var f = new ol.Feature({
            geometry: new ol.geom.LineString(coordsMap),
        });

        f.set('featureType', 'tram_line');
        f.set('section', sectionName || '');
        f.setStyle(getTramLineStyleByColor(color));

        tramLineSource.addFeature(f);
    }

    function drawTramLine(tramData) {
        if (!olMap) return;
        ensureMapProjCode();
        tramLineSource.clear();
        if (!tramData || !tramData.length) return;

        var currentSection = null;
        var currentCoords = [];

        tramData.forEach(function (p) {
            if (!p) return;

            var lat = parseFloat(p.lat);
            var lng = parseFloat(p.lng);
            if (isNaN(lat) || isNaN(lng)) return;

            var sectionName = p.section || '';
            var xyMap = lonLatToMapXY(lng, lat);

            if (currentSection === null) {
                currentSection = sectionName;
                currentCoords = [xyMap];
                return;
            }

            if (sectionName !== currentSection) {
                addTramSegmentFeature(currentCoords, currentSection);
                var lastPointOfPrev = currentCoords.length > 0 ? currentCoords[currentCoords.length - 1] : null;
                currentSection = sectionName;
                if (lastPointOfPrev) {
                    currentCoords = [lastPointOfPrev, xyMap];
                } else {
                    currentCoords = [xyMap];
                }
                return;
            }
            currentCoords.push(xyMap);
        });
        addTramSegmentFeature(currentCoords, currentSection);
    }

    function drawTramStops(tramData) {
        if (!olMap) return;
        ensureMapProjCode();
        tramStopSource.clear();
        if (!tramData || !tramData.length) return;

        tramData.forEach(function (p) {
            if (!p) return;
            if (p.type === 'waypoint') return;
            if (!isIntegerId(p.id)) return;

            var lat = parseFloat(p.lat);
            var lng = parseFloat(p.lng);
            if (isNaN(lat) || isNaN(lng)) return;

            var sectionColor = getTramSectionColor(p.section);
            var xyMap = lonLatToMapXY(lng, lat);

            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xyMap),
            });

            feature.set('featureType', 'tram_stop');
            feature.setStyle([
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 6,
                        fill: new ol.style.Fill({ color: '#ffffff' }),
                        stroke: new ol.style.Stroke({ color: sectionColor, width: 3 }),
                    }),
                    zIndex: 8,
                }),
                new ol.style.Style({
                    text: new ol.style.Text({
                        text: String(p.id),
                        font: 'bold 12px "Pretendard", sans-serif',
                        fill: new ol.style.Fill({ color: '#111' }),
                        stroke: new ol.style.Stroke({ color: '#fff', width: 4 }),
                        offsetY: -16,
                        textAlign: 'center',
                    }),
                    zIndex: 9,
                }),
            ]);
            tramStopSource.addFeature(feature);
        });
    }

    function drawTramOnMapIfExists() {
        var data = window.TRAM_ROUTE_FULL_HD || window.TRAM_STATIONS || null;
        if (!data || !data.length) {
            clearTram();
            return;
        }
        drawTramLine(data);
        drawTramStops(data);
    }

    $scope.toggleTramLayer = function () {
        $scope.isTramVisible = !$scope.isTramVisible;
        if ($scope.isTramVisible) {
            drawTramOnMapIfExists();
        } else {
            clearTram();
        }
    };

    // -------------------------
    // 벡터 레이어 준비 (정류장/버스)
    // -------------------------
    var stopSource = new ol.source.Vector();
    var stopLayer = new ol.layer.Vector({
        source: stopSource,
        zIndex: 10,
    });

    var busSource = new ol.source.Vector();
    var busLayer = new ol.layer.Vector({
        source: busSource,
        zIndex: 20,
    });

    // -------------------------
    // 노선 라인 레이어 (파란색)
    // -------------------------
    var routeLineSource = new ol.source.Vector();
    var routeLineLayer = new ol.layer.Vector({
        source: routeLineSource,
        zIndex: 5,
        style: new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: 'rgba(0, 102, 255, 0.7)',
                width: 5,
                lineCap: 'round',
                lineJoin: 'round',
            }),
        }),
    });

    // -------------------------
    // [추가] 최단 경로 (Path) 레이어
    // -------------------------
    var pathSource = new ol.source.Vector();
    var pathLayer = new ol.layer.Vector({
        source: pathSource,
        zIndex: 500, // 가장 위에 표시
        style: function (feature) {
            var mode = feature.get('mode');

            // BUS 환승(노선 변경) 시 색상을 바꾸기 위한 팔레트
            // - drawCalculatedPath()에서 BUS 구간마다 busTransferIndex를 세팅한다.
            // - 같은 노선(routeId)이면 같은 색, 노선이 바뀌면 다음 색을 사용한다.
            var busColors = ['#2E86AB', '#F18F01', '#C73E1D', '#6A4C93', '#2A9D8F', '#E76F51'];

            // WALK: 회색 점선, BUS: 환승 인덱스 기반 실선(기본 0)
            var busIdx = feature.get('busTransferIndex');
            if (busIdx == null || isNaN(busIdx)) busIdx = 0;
            var updowncd = feature.get('updowncd'); // BUS 방향(0/1)

            var color = mode === 'WALK' ? '#555555' : busColors[busIdx % busColors.length];
            var width = mode === 'WALK' ? 4 : 6;
            var lineDash = mode === 'WALK' ? [10, 10] : null;

            if (mode === 'BUS' && updowncd === 1) {
                // 하행은 점선으로 구분 (동일한 라인이라도 방향 차이를 눈으로 확인 가능)
                lineDash = null;
            }

            if (mode === 'TRAM') {
                color = '#FF69B4'; // 핫핑크
                width = 6;
                lineDash = null;
            }

            return new ol.style.Style({
                stroke: new ol.style.Stroke({
                    color: color,
                    width: width,
                    lineDash: lineDash,
                    lineCap: 'round',
                }),
            });
        },
    });

    // -------------------------
    // [추가] 최단경로(BUS) routeId -> 버스번호(routenm) 매핑 캐시
    //  - Path API는 BUS 구간에 routeId(DJB...)만 내려주므로,
    //    기존 /api/bus/locations 호출 결과의 routenm(=버스 번호/명칭)을 이용해 표시한다.
    // -------------------------
    var pathRouteNoMap = {}; // { routeId: '101', ... }
    var pathRouteNoLoadingMap = {}; // { routeId: true } 중복 호출 방지

    // -------------------------
    // [추가] 최단경로 정류장(nodeId) -> 정류장 이름(nodenm) 캐싱
    //  - Path API가 BUS 노드 이름을 내려주지 않으므로(현재 DB에 이름 컬럼 없음),
    //    BUS 정류장 hover 시에만 좌표 기반 근접 정류장 API로 보완한다.
    // -------------------------
    var pathNodeNameCache = {}; // { nodeId: '정부청사역(…)', ... }
    var pathNodeNamePending = {}; // { nodeId: true } 중복 호출 방지

    function extractRouteNoFromBusLocationResponse(data) {
        // TAGO 응답 형태 방어적 처리
        if (!data || !data.response || !data.response.body) return null;
        var items = data.response.body.items && data.response.body.items.item;
        if (!items) return null;

        var arr = angular.isArray(items) ? items : [items];
        if (!arr.length) return null;

        // 위치 API 응답의 routenm(또는 routeno)을 버스 번호/명칭으로 사용
        var first = arr[0] || {};
        var rn = (first.routenm != null ? String(first.routenm) : '') || (first.routeno != null ? String(first.routeno) : '');
        rn = rn.trim();
        if (!rn) return null;

        // "101" 처럼 숫자만 올 수도 있고, "101" 외 텍스트가 섞일 수도 있음 → 그대로 사용
        return rn;
    }

    function prefetchPathBusRouteNosByRouteIds(routeIds) {
        if (!routeIds || !routeIds.length) return;

        routeIds.forEach(function (rid) {
            if (!rid) return;
            if (pathRouteNoMap[rid]) return;
            if (pathRouteNoLoadingMap[rid]) return;

            pathRouteNoLoadingMap[rid] = true;

            $http
                .get('/api/bus/locations', {
                    params: { routeId: rid, pageNo: 1, numOfRows: 1 },
                })
                .then(function (res) {
                    var parsed = parseMaybeJson(res.data);
                    var routeNo = extractRouteNoFromBusLocationResponse(parsed);
                    if (routeNo) {
                        pathRouteNoMap[rid] = routeNo;
                    }
                })
                .catch(function (err) {
                    console.warn('최단경로 버스번호 매핑용 locations 호출 실패:', rid, err);
                })
                .finally(function () {
                    pathRouteNoLoadingMap[rid] = false;
                });
        });
    }

    function extractItemsFromTagoResponse(data) {
        if (!data || !data.response || !data.response.body) return [];
        var items = data.response.body.items && data.response.body.items.item;
        if (!items) return [];
        return angular.isArray(items) ? items : [items];
    }

    function resolvePathBusStopName(nodeId, wgsLat, wgsLng, onResolved) {
        if (!nodeId) return;
        if (pathNodeNameCache[nodeId]) {
            if (typeof onResolved === 'function') onResolved(pathNodeNameCache[nodeId]);
            return;
        }
        if (pathNodeNamePending[nodeId]) return;

        // 좌표가 없으면 조회 불가
        if (wgsLat == null || wgsLng == null || isNaN(wgsLat) || isNaN(wgsLng)) return;

        pathNodeNamePending[nodeId] = true;

        $http
            .get('/api/bus/stops-nearby', {
                params: { lat: wgsLat, lng: wgsLng, pageNo: 1, numOfRows: 50 },
            })
            .then(function (res) {
                var data = parseMaybeJson(res.data);
                var arr = extractItemsFromTagoResponse(data);
                for (var i = 0; i < arr.length; i++) {
                    var it = arr[i] || {};
                    var nid = it.nodeid != null ? String(it.nodeid) : it.nodeId != null ? String(it.nodeId) : '';
                    if (!nid) continue;
                    if (nid === nodeId) {
                        var nm = it.nodenm != null ? String(it.nodenm) : it.nodeNm != null ? String(it.nodeNm) : '';
                        nm = (nm || '').trim();
                        if (nm) {
                            pathNodeNameCache[nodeId] = nm;
                            if (typeof onResolved === 'function') onResolved(nm);
                        }
                        break;
                    }
                }
            })
            .catch(function (err) {
                console.warn('근접 정류장 조회 실패(nodeName 보완):', nodeId, err);
            })
            .finally(function () {
                pathNodeNamePending[nodeId] = false;
            });
    }

    // -------------------------
    // 노선 라인 화살표
    // -------------------------
    var ROUTE_ARROW_EVERY_N_SEGMENTS = 2;
    var ROUTE_ARROW_MIN_SEGMENT_LEN = 30;
    var ROUTE_ARROW_ROTATION_OFFSET = 0;
    var routeArrowStyleCache = {};

    function buildRouteArrowSvgDataUri(fillColor) {
        var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' + '<path fill="' + fillColor + '" d="M4 12h11.2l-3.6-3.6L13 7l7 7-7 7-1.4-1.4 3.6-3.6H4z"/>' + '</svg>';
        return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
    }

    function getRouteArrowStyle(rotationRad) {
        // [수정] 화살표 회전 각도 보정: 단순히 각도를 반전(-rotationRad)하면 됨
        // (SVG 아이콘이 오른쪽(0도)을 바라보고 있기 때문)
        var rot = -rotationRad;

        var key = (Math.round(rot * 100) / 100).toString();
        if (routeArrowStyleCache[key]) return routeArrowStyleCache[key];

        routeArrowStyleCache[key] = new ol.style.Style({
            image: new ol.style.Icon({
                src: buildRouteArrowSvgDataUri('#0066ff'),
                imgSize: [24, 24],
                anchor: [0.5, 0.5],
                anchorXUnits: 'fraction',
                anchorYUnits: 'fraction',
                rotateWithView: true,
                rotation: rot,
                scale: 0.7,
                opacity: 0.95,
            }),
        });

        return routeArrowStyleCache[key];
    }

    // -------------------------
    // 툴팁 (Hover)
    // -------------------------
    var hoverTooltipEl = null;
    var hoverTooltipOverlay = null;
    var lastHoverFeature = null;
    var lastHoverCoord = null;

    function initHoverTooltip() {
        if (!olMap) return;
        if (hoverTooltipOverlay) return;

        var mapDiv = document.getElementById('map1');
        if (!mapDiv) return;

        hoverTooltipEl = document.createElement('div');
        hoverTooltipEl.style.position = 'absolute';
        hoverTooltipEl.style.pointerEvents = 'none';
        hoverTooltipEl.style.background = 'rgba(0, 0, 0, 0.8)';
        hoverTooltipEl.style.color = '#ffffff';
        hoverTooltipEl.style.padding = '8px 12px';
        hoverTooltipEl.style.borderRadius = '6px';
        hoverTooltipEl.style.fontSize = '13px';
        hoverTooltipEl.style.whiteSpace = 'nowrap';
        hoverTooltipEl.style.display = 'none';
        hoverTooltipEl.style.zIndex = '9999';
        hoverTooltipEl.style.boxShadow = '0 2px 5px rgba(0,0,0,0.2)';

        mapDiv.appendChild(hoverTooltipEl);

        hoverTooltipOverlay = new ol.Overlay({
            element: hoverTooltipEl,
            offset: [15, 0],
            positioning: 'center-left',
            stopEvent: false,
        });

        olMap.addOverlay(hoverTooltipOverlay);

        mapDiv.addEventListener('mouseleave', function () {
            hideHoverTooltip();
        });

        olMap.on('pointermove', function (evt) {
            if (evt.dragging) {
                hideHoverTooltip();
                return;
            }

            var isRouteMode = !!$scope.currentRouteId;
            var isStopSearchMode = !isRouteMode && $scope.stops && $scope.stops.length > 0;
            var hasPathResult = pathSource.getFeatures().length > 0;

            if (!isRouteMode && !isStopSearchMode && !hasPathResult) {
                hideHoverTooltip();
                return;
            }

            var pixel = olMap.getEventPixel(evt.originalEvent);

            var feature = olMap.forEachFeatureAtPixel(
                pixel,
                function (f) {
                    return f;
                },
                {
                    layerFilter: function (layer) {
                        return layer !== repPulseLayer;
                    },
                }
            );

            if (!feature) {
                hideHoverTooltip();
                return;
            }

            // 현재 hover 중인 feature/좌표를 저장(비동기 이름 보완 시 툴팁 갱신에 사용)
            lastHoverFeature = feature;
            lastHoverCoord = evt.coordinate;

            lastHoverFeature = feature;
            lastHoverCoord = evt.coordinate;

            var fType = feature.get('featureType');

            // 1. [수정] 최단경로 선(Line) 위 마우스 오버
            if (fType === 'path_segment') {
                var mode = feature.get('mode'); // BUS, TRAM, WALK
                var min = feature.get('minutes') || 0;
                var routeId = feature.get('routeId'); // TAGO routeId(DJB...)

                if (min < 1) min = 1;

                var text = '';
                if (mode === 'WALK') {
                    text = '🚶 도보 ' + min + '분';
                } else if (mode === 'BUS') {
                    // routeId -> 버스번호(예: 101)로 변환해서 표기
                    var busNo = routeId && pathRouteNoMap && pathRouteNoMap[routeId] ? String(pathRouteNoMap[routeId]) : null;
                    if (busNo) {
                        text = '🚌 버스(' + busNo + '번) : ' + min + '분';
                    } else {
                        // 아직 매핑이 없으면 routeId를 임시로 표기
                        text = '🚌 버스' + (routeId ? '(' + routeId + ')' : '') + ' : ' + min + '분';
                    }
                } else if (mode === 'TRAM') {
                    text = '🚋 트램 2호선 : ' + min + '분';
                } else {
                    text = '이동 ' + min + '분';
                }

                showHoverTooltip(evt.coordinate, text);
                return;
            }

            // 2. [추가] 최단경로 정류장(Node) 위 마우스 오버
            if (fType === 'path_node') {
                var nodeMode = feature.get('mode');
                var nodeId = feature.get('nodeId') || null;
                var nodeName = feature.get('nodeName') || null;

                if (nodeMode === 'TRAM') {
                    // TRAM은 Path API에서 nodeNames로 내려줌
                    var tramLabel = nodeName ? '🚋 ' + nodeName : '🚋 트램 정거장';
                    showHoverTooltip(evt.coordinate, tramLabel);
                    return;
                }

                // BUS 정류장은 이름을 못 받으면(현재 DB에 없음) 좌표 기반 근접 정류장 API로 보완
                if (nodeName) {
                    showHoverTooltip(evt.coordinate, '🚏 ' + nodeName);
                    return;
                }

                var wgsLat = feature.get('wgsLat');
                var wgsLng = feature.get('wgsLng');
                showHoverTooltip(evt.coordinate, '🚏 정류장' + (nodeId ? '(' + nodeId + ')' : ''));

                resolvePathBusStopName(nodeId, wgsLat, wgsLng, function (resolvedName) {
                    // 현재 hover 중인 동일 feature인 경우에만 갱신(툴팁 점프 방지)
                    try {
                        feature.set('nodeName', resolvedName);
                    } catch (e) {}

                    if (lastHoverFeature === feature && lastHoverCoord) {
                        showHoverTooltip(lastHoverCoord, '🚏 ' + resolvedName);
                    }
                });
                return;
            }

            if (fType === 'stop') {
                var stopData = feature.get('stopData') || null;
                var stopName = (stopData && (stopData.nodenm || stopData.stationName)) || feature.get('name') || '';

                if (!stopName) {
                    hideHoverTooltip();
                    return;
                }
                showHoverTooltip(evt.coordinate, '🚏 ' + stopName);
                return;
            }

            if (fType === 'bus') {
                var busData = feature.get('busData') || null;
                if (!busData) {
                    hideHoverTooltip();
                    return;
                }

                var routeNo = (busData.routenm != null ? String(busData.routenm) : '') || (busData.routeno != null ? String(busData.routeno) : '') || '';
                var vehicleNo = (busData.vehicleno != null ? String(busData.vehicleno) : '') || '';

                var parts = [];
                if (routeNo) parts.push(routeNo + '번');
                if (vehicleNo) parts.push(vehicleNo);

                if (isRouteMode) {
                    var calc = computePrevCurrentNextForBus(busData, $scope.stops || []);
                    var nextStopName = (calc && calc.next && (calc.next.nodenm || calc.next.stationName)) || '';
                    if (nextStopName) parts.push('→ ' + nextStopName);
                }

                var text = parts.join(' | ');
                if (!text) {
                    hideHoverTooltip();
                    return;
                }
                showHoverTooltip(evt.coordinate, '🚌 ' + text);
                return;
            }

            hideHoverTooltip();
        });
    }

    function showHoverTooltip(coord, text) {
        if (!hoverTooltipEl || !hoverTooltipOverlay) return;
        hoverTooltipEl.textContent = text;
        hoverTooltipEl.style.display = 'block';
        hoverTooltipOverlay.setPosition(coord);
    }

    function hideHoverTooltip() {
        if (!hoverTooltipEl || !hoverTooltipOverlay) return;
        hoverTooltipEl.style.display = 'none';
        hoverTooltipOverlay.setPosition(undefined);
    }

    // -------------------------
    // 대표 버스 펄스(파동) 애니메이션
    // -------------------------
    var repPulseSource = new ol.source.Vector();
    var repPulseLayer = new ol.layer.Vector({
        source: repPulseSource,
        zIndex: 15,
        style: function () {
            if (!$scope.representativeBus) return null;
            if (!$scope.currentRouteId) return null;

            var t = Date.now();
            var phase = (t % 1500) / 1500.0;
            var radius = 5 + phase * 20;
            var opacity = 1.0 - phase;

            var pulseColor = '255, 149, 0';

            return new ol.style.Style({
                image: new ol.style.Circle({
                    radius: radius,
                    stroke: new ol.style.Stroke({
                        color: 'rgba(' + pulseColor + ', ' + opacity.toFixed(3) + ')',
                        width: 2 + 2 * (1 - phase),
                    }),
                    fill: new ol.style.Fill({
                        color: 'rgba(' + pulseColor + ', ' + (opacity * 0.1).toFixed(3) + ')',
                    }),
                }),
            });
        },
    });

    var repPulseFeature = null;
    var repPulseRafId = null;

    function startRepPulseAnimationLoop() {
        if (!olMap) return;
        if (repPulseRafId != null) return;

        var tick = function () {
            if (!olMap || !$scope.representativeBus || !$scope.currentRouteId) {
                repPulseRafId = null;
                return;
            }
            olMap.render();
            repPulseRafId = requestAnimationFrame(tick);
        };
        repPulseRafId = requestAnimationFrame(tick);
    }

    function stopRepPulseAnimationLoop() {
        if (repPulseRafId != null) {
            cancelAnimationFrame(repPulseRafId);
            repPulseRafId = null;
        }
    }

    function clearRepPulse() {
        repPulseSource.clear();
        repPulseFeature = null;
        stopRepPulseAnimationLoop();
    }

    function updateRepPulseFeatureByBus(bus) {
        if (!olMap) return;
        ensureMapProjCode(); // ✅ 지도 projection 확정

        if (!bus) {
            clearRepPulse();
            return;
        }

        var lat = parseFloat(bus.gpslati);
        var lon = parseFloat(bus.gpslong);
        if (isNaN(lat) || isNaN(lon)) {
            clearRepPulse();
            return;
        }

        var xyMap = lonLatToMapXY(lon, lat);

        if (!repPulseFeature) {
            repPulseFeature = new ol.Feature({
                geometry: new ol.geom.Point(xyMap),
            });
            repPulseSource.addFeature(repPulseFeature);
        } else {
            repPulseFeature.setGeometry(new ol.geom.Point(xyMap));
        }
        startRepPulseAnimationLoop();
    }

    // -------------------------
    // 대표 버스 지도 이동
    // -------------------------
    var lastRepVehicleNoForPan = null;
    var lastRepPanAtMs = 0;
    var REP_ZOOM_IN_DELTA = 1;
    var REP_ZOOM_MAX = 15;

    function panToRepresentativeBusIfNeeded(bus) {
        if (!olMap) return;
        ensureMapProjCode(); // ✅ 지도 projection 확정
        if (!bus) return;
        if (!$scope.currentRouteId) return;

        var vehicleno = bus.vehicleno != null ? String(bus.vehicleno) : null;
        if (!vehicleno) return;

        if (lastRepVehicleNoForPan === vehicleno) return;

        var now = Date.now();
        if (now - lastRepPanAtMs < 1000) return;

        var lat = parseFloat(bus.gpslati);
        var lon = parseFloat(bus.gpslong);
        if (isNaN(lat) || isNaN(lon)) return;

        var centerMap = lonLatToMapXY(lon, lat);

        var view = olMap.getView();
        if (!view) return;

        var currentZoom = view.getZoom();
        var targetZoom = currentZoom;
        if (typeof currentZoom === 'number') {
            targetZoom = Math.min(REP_ZOOM_MAX, currentZoom + REP_ZOOM_IN_DELTA);
        }

        view.animate({ center: centerMap, duration: 800 }, { zoom: targetZoom, duration: 800 });

        lastRepVehicleNoForPan = vehicleno;
        lastRepPanAtMs = now;
    }

    // -------------------------
    // JSON 파싱 함수
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
    // 정류장 모드: 버스 클릭 이벤트
    // -------------------------
    function initBusClickToShowRouteLine() {
        if (!olMap) return;
        if (olMap.__busClickToRouteLineBound) return;

        olMap.__busClickToRouteLineBound = true;

        olMap.on('singleclick', function (evt) {
            if (!olMap) return;

            if ($scope.isRoutePickerOn) return;

            var isRouteMode = !!$scope.currentRouteId;
            var isStopSearchMode = !isRouteMode && $scope.stops && $scope.stops.length > 0;
            if (!isStopSearchMode) return;

            var pixel = olMap.getEventPixel(evt.originalEvent);

            var feature = olMap.forEachFeatureAtPixel(
                pixel,
                function (f) {
                    return f;
                },
                {
                    layerFilter: function (layer) {
                        return layer !== repPulseLayer;
                    },
                }
            );

            if (!feature) return;
            if (feature.get('featureType') !== 'bus') return;

            var busData = feature.get('busData') || null;
            if (!busData) return;

            var routeId = busData.routeid || busData.routeId || busData.route_id || null;
            if (!routeId) {
                console.warn('버스 클릭 감지했지만 routeId 없음:', busData);
                return;
            }

            $scope.tempRouteIdFromStop = String(routeId);

            clearRouteLine();

            $http
                .get('/api/bus/route-stops', {
                    params: { routeId: routeId },
                })
                .then(function (res) {
                    var data = parseMaybeJson(res.data);
                    if (!data || !data.response || !data.response.body) return;

                    var items = data.response.body.items && data.response.body.items.item;
                    if (!items) return;

                    var stopsArray = angular.isArray(items) ? items : [items];
                    drawRouteLineFromStops(stopsArray);

                    if (!$scope.$$phase) $scope.$applyAsync();
                })
                .catch(function (err) {
                    console.error('버스 클릭 → 노선 정류장 조회 실패:', err);
                });
        });
    }

    // -------------------------
    // 지도 초기화 함수
    // -------------------------
    $scope.initMap = function () {
        var mapDiv = document.getElementById('map1');

        if (!window.ngii_wmts || !mapDiv) {
            console.error('NGII 지도 스크립트 미로드');
            return;
        }

        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 3,
        });

        if (typeof $scope.map1._getMap === 'function') {
            olMap = $scope.map1._getMap();
        } else {
            console.warn('_getMap 함수 없음');
            olMap = null;
        }

        $scope.olMap = olMap;
        window.__olMap = olMap;

        // ✅ 여기서 MAP_PROJ_CODE 확정(중요)
        MAP_PROJ_CODE = null;
        ensureMapProjCode();

        if (olMap && typeof olMap.addLayer === 'function') {
            olMap.addLayer(tramLineLayer);
            olMap.addLayer(tramStopLayer);

            olMap.addLayer(routeLineLayer);
            olMap.addLayer(stopLayer);
            olMap.addLayer(busLayer);
            olMap.addLayer(repPulseLayer);

            // ★ [추가] 최단 경로 레이어 추가
            olMap.addLayer(pathLayer);

            console.log('레이어 추가 완료 (트램, 최단경로 포함)');
        }

        initHoverTooltip();
        initBusClickToShowRouteLine();

        clearTram();
        $scope.isTramVisible = false;

        // Collector 상태 초기화
        refreshCollectorStatus();
        startCollectorPoll();
    };

    $timeout($scope.initMap, 0);

    // -------------------------
    // 노선 라인 관련 함수
    // -------------------------
    function clearRouteLine() {
        routeLineSource.clear();
    }

    function drawRouteLineFromStops(stops) {
        var routeIdForLine = $scope.currentRouteId || $scope.tempRouteIdFromStop;
        if (!routeIdForLine) {
            clearRouteLine();
            return;
        }

        clearRouteLine();

        if (!olMap) return;
        ensureMapProjCode(); // ✅ 지도 projection 확정
        if (!stops || stops.length < 2) return;

        var sortedStops = stops.slice().sort(function (a, b) {
            var sa = parseInt(a.routeseq || a.routeSeq || 0, 10);
            var sb = parseInt(b.routeseq || b.routeSeq || 0, 10);
            return sa - sb;
        });

        var coordinates = [];
        sortedStops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);
            if (!isNaN(lat) && !isNaN(lon)) {
                var xyMap = lonLatToMapXY(lon, lat);
                coordinates.push(xyMap);
            }
        });

        if (coordinates.length < 2) return;

        var lineFeature = new ol.Feature({
            geometry: new ol.geom.LineString(coordinates),
        });
        routeLineSource.addFeature(lineFeature);

        for (var i = 0; i < coordinates.length - 1; i++) {
            if (ROUTE_ARROW_EVERY_N_SEGMENTS > 1 && i % ROUTE_ARROW_EVERY_N_SEGMENTS !== 0) continue;

            var p1 = coordinates[i];
            var p2 = coordinates[i + 1];
            if (!p1 || !p2) continue;

            var dx = p2[0] - p1[0];
            var dy = p2[1] - p1[1];
            var segLen = Math.sqrt(dx * dx + dy * dy);

            if (!isFinite(segLen) || segLen < ROUTE_ARROW_MIN_SEGMENT_LEN) continue;

            var mid = [(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2];
            var angle = Math.atan2(dy, dx);

            var arrowFeature = new ol.Feature({
                geometry: new ol.geom.Point(mid),
            });
            arrowFeature.setStyle(getRouteArrowStyle(angle));
            routeLineSource.addFeature(arrowFeature);
        }

        var extent = routeLineSource.getExtent();
        if (extent && isFinite(extent[0])) {
            var view = olMap.getView();
            if (view) {
                view.fit(extent, {
                    padding: [60, 60, 60, 60],
                    maxZoom: 14,
                    duration: 500,
                });
            }
        }
    }

    // -------------------------
    // 정류장 마커 관련 함수
    // -------------------------
    function clearStopMarkers() {
        var newSrc = new ol.source.Vector();
        stopLayer.setSource(newSrc);
        stopSource = newSrc;
    }

    function addStopMarkerToSource(targetSource, lat, lon, title, stopData, isSelected) {
        if (!olMap) return;
        ensureMapProjCode(); // ✅ 지도 projection 확정
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xyMap = lonLatToMapXY(lon, lat);

            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xyMap),
                name: title || '',
            });

            feature.set('featureType', 'stop');
            feature.set('stopData', stopData || null);

            var fillColor = isSelected ? '#007bff' : '#ffffff';
            var strokeColor = isSelected ? '#ffffff' : '#555555';
            var strokeWidth = isSelected ? 3 : 2;
            var radiusVal = isSelected ? 8 : 5;
            var zIndexVal = isSelected ? 999 : 10;

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: radiusVal,
                        fill: new ol.style.Fill({ color: fillColor }),
                        stroke: new ol.style.Stroke({ color: strokeColor, width: strokeWidth }),
                    }),
                    zIndex: zIndexVal,
                })
            );

            targetSource.addFeature(feature);
        } catch (e) {
            console.warn('정류장 마커 오류:', e);
        }
    }

    function fitMapToStops() {
        if (!olMap) return;
        var extent = stopSource.getExtent();
        if (!extent || !isFinite(extent[0])) return;

        var view = olMap.getView();
        if (view) {
            view.fit(extent, {
                padding: [50, 50, 50, 50],
                maxZoom: 14,
                duration: 500,
            });
        }
    }

    function drawStopsOnMap(stops) {
        if (!stops || !stops.length) {
            clearStopMarkers();
            return;
        }
        var newSrc = new ol.source.Vector();
        stops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            var isSelected = $scope.selectedStop && s === $scope.selectedStop;

            if (!isNaN(lat) && !isNaN(lon)) {
                addStopMarkerToSource(newSrc, lat, lon, s.nodenm || s.stationName || '', s, isSelected);
            }
        });
        stopLayer.setSource(newSrc);
        stopSource = newSrc;

        if (!$scope.selectedStop) {
            fitMapToStops();
        }
    }

    // -------------------------
    // 버스 마커 관련 함수
    // -------------------------
    function clearBusMarkers() {
        var newSrc = new ol.source.Vector();
        busLayer.setSource(newSrc);
        busSource = newSrc;
    }

    function addBusMarkerToSource(targetSource, lat, lon, title, isRepresentative, busData) {
        if (!olMap) return;
        ensureMapProjCode(); // ✅ 지도 projection 확정
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xyMap = lonLatToMapXY(lon, lat);

            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xyMap),
                name: title || '',
            });

            feature.set('featureType', 'bus');
            feature.set('busData', busData || null);

            var busColor = isRepresentative ? '#ff9500' : '#007bff';
            var iconScale = isRepresentative ? 0.05 : 0.03;
            var zIndexVal = isRepresentative ? 100 : 50;

            var busNoText = '';
            if (!$scope.currentRouteId && title != null) {
                busNoText = String(title).trim();
            }

            var styleArray = [
                new ol.style.Style({
                    image: new ol.style.Icon({
                        src: createSvgIcon(busColor, 'bus'),
                        anchor: [0.5, 0.5],
                        scale: iconScale,
                        opacity: 1.0,
                        rotation: 0,
                    }),
                    zIndex: zIndexVal,
                }),
            ];

            if (busNoText) {
                styleArray.push(
                    new ol.style.Style({
                        text: new ol.style.Text({
                            text: busNoText,
                            font: 'bold 12px "Pretendard", sans-serif',
                            fill: new ol.style.Fill({ color: '#333' }),
                            stroke: new ol.style.Stroke({ color: '#fff', width: 3 }),
                            offsetY: -15,
                            textAlign: 'center',
                        }),
                        zIndex: zIndexVal + 1,
                    })
                );
            }

            feature.setStyle(styleArray);
            targetSource.addFeature(feature);
        } catch (e) {
            console.warn('버스 마커 오류:', e);
        }
    }

    function drawBusLocationsOnMap(busItems) {
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
                    isRepresentative = rep.vehicleno === b.vehicleno;
                }
                addBusMarkerToSource(newSrc, lat, lon, String(label).trim(), isRepresentative, b);
            }
        });

        busLayer.setSource(newSrc);
        busSource = newSrc;
    }

    // -------------------------
    // API 호출 및 데이터 처리
    // -------------------------
    function computePrevCurrentNextForBus(bus, stops) {
        var result = { prev: null, current: null, next: null };
        if (!bus || !stops || !stops.length) return result;

        var currentIndex = -1;
        var busNodeId = bus.nodeid || bus.nodeId || null;
        var busSeq = bus.routeseq || bus.routeSeq || null;

        if (busNodeId) {
            for (var i = 0; i < stops.length; i++) {
                var s = stops[i];
                if ((s.nodeid || s.nodeId) === busNodeId) {
                    currentIndex = i;
                    break;
                }
            }
        }

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

        if (currentIndex === -1) return result;
        result.current = stops[currentIndex];
        if (currentIndex > 0) result.prev = stops[currentIndex - 1];
        if (currentIndex < stops.length - 1) result.next = stops[currentIndex + 1];
        return result;
    }

    function drawBusesForArrivalRoutes(arrivals) {
        if ($scope.currentRouteId) return;
        $scope.representativeBus = null;
        clearRepPulse();
        lastRepVehicleNoForPan = null;

        if (!arrivals || !arrivals.length) {
            clearBusMarkers();
            return;
        }

        var routeIdMap = {};
        arrivals.forEach(function (a) {
            var rid = a.routeid || a.routeId || a.route_id;
            if (rid) routeIdMap[rid] = true;
        });

        var routeIds = Object.keys(routeIdMap);
        if (!routeIds.length) {
            clearBusMarkers();
            return;
        }

        lastArrivalDrawRequestId++;
        var myReqId = lastArrivalDrawRequestId;
        var pending = routeIds.length;
        var tempSource = new ol.source.Vector();

        routeIds.forEach(function (rid) {
            $http
                .get('/api/bus/locations', {
                    params: { routeId: rid, pageNo: 1, numOfRows: 100 },
                })
                .then(function (res) {
                    if (myReqId !== lastArrivalDrawRequestId) return;
                    var data = parseMaybeJson(res.data);
                    if (!data || !data.response || !data.response.body) return;
                    var items = data.response.body.items && data.response.body.items.item;
                    if (!items) return;

                    var busArray = angular.isArray(items) ? items : [items];
                    busArray.forEach(function (b) {
                        if (!b.routeid && !b.routeId && !b.route_id) b.routeid = rid;
                        var lat = parseFloat(b.gpslati);
                        var lon = parseFloat(b.gpslong);
                        if (isNaN(lat) || isNaN(lon)) return;
                        var label = b.routenm != null ? String(b.routenm) : '';
                        addBusMarkerToSource(tempSource, lat, lon, String(label).trim(), false, b);
                    });
                })
                .catch(function (err) {
                    console.error('정류장 모드 버스 위치 조회 실패:', err);
                })
                .finally(function () {
                    if (myReqId !== lastArrivalDrawRequestId) return;
                    pending--;
                    if (pending === 0) {
                        busLayer.setSource(tempSource);
                        busSource = tempSource;
                    }
                });
        });
    }

    function fetchArrivalsForCurrentStop() {
        if (!$scope.currentStop) return;
        var nodeId = $scope.currentStop.nodeid || $scope.currentStop.nodeId;
        if (!nodeId) return;

        var previousArrivalList = $scope.arrivalList || [];

        $http
            .get('/api/bus/arrivals', {
                params: { nodeId: nodeId, numOfRows: 20 },
            })
            .then(function (res) {
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
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
                    var remainStops = a.arrprevstationcnt != null ? parseInt(a.arrprevstationcnt, 10) : null;
                    var sec = a.arrtime != null ? parseInt(a.arrtime, 10) : null;
                    var minutes = null;
                    if (!isNaN(sec) && sec != null) minutes = Math.round(sec / 60.0);
                    return angular.extend({}, a, {
                        remainStops: isNaN(remainStops) ? null : remainStops,
                        remainMinutes: minutes,
                    });
                });
                $scope.arrivalList = mapped;
                drawBusesForArrivalRoutes($scope.arrivalList);
            })
            .catch(function (err) {
                console.error('도착 정보 조회 실패:', err);
                $scope.arrivalList = previousArrivalList;
            });
    }

    $scope.selectStop = function (stop) {
        if (!stop) return;
        $scope.selectedStop = stop;
        $scope.currentStop = stop;

        fetchArrivalsForCurrentStop();

        drawStopsOnMap($scope.stops);

        if (olMap) {
            ensureMapProjCode(); // ✅ 지도 projection 확정
            var lat = parseFloat(stop.gpslati || stop.gpsLati || stop.gpsY);
            var lon = parseFloat(stop.gpslong || stop.gpsLong || stop.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                var center = lonLatToMapXY(lon, lat);
                var view = olMap.getView();
                if (view) {
                    view.animate({
                        center: center,
                        zoom: 17,
                        duration: 500,
                    });
                }
            }
        }
    };

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
        clearRepPulse();

        if ($scope.isRoutePickerOn) {
            $scope.disableRoutePicker();
        }

        stopCollectorPoll(); // ✅ collector 폴링 정리
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

    $scope.searchBus = function () {
        if (!$scope.searchTerm) {
            alert('버스 번호를 입력하세요.');
            return;
        }
        var routeNo = $scope.searchTerm;
        cancelAutoRefresh();

        $http
            .get('/api/bus/routes', { params: { routeNo: routeNo } })
            .then(function (res) {
                $scope.routeResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    alert('노선 정보를 찾을 수 없습니다.');
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
                    alert('routeId 없음');
                    return;
                }

                $scope.currentRouteId = routeId;
                $scope.representativeBus = null;
                $scope.prevStop = null;
                $scope.currentStop = null;
                $scope.nextStop = null;
                $scope.arrivalList = [];
                $scope.selectedStop = null;
                $scope.tempRouteIdFromStop = null;
                lastRepVehicleNoForPan = null;

                $scope.fetchRouteStops(routeId);
                $scope.fetchBusLocations();
                startAutoRefresh();
            })
            .catch(function (err) {
                console.error('노선 조회 실패:', err);
                alert('노선 정보를 가져오지 못했습니다.');
            });
    };

    $scope.fetchRouteStops = function (routeId) {
        if (!routeId) return;
        $http
            .get('/api/bus/route-stops', { params: { routeId: routeId } })
            .then(function (res) {
                $scope.stopsResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
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
                $scope.selectedStop = null;

                drawStopsOnMap(stopsArray);
                drawRouteLineFromStops(stopsArray);

                if ($scope.representativeBus) {
                    var calc = computePrevCurrentNextForBus($scope.representativeBus, $scope.stops);
                    $scope.prevStop = calc.prev;
                    $scope.currentStop = calc.current;
                    $scope.nextStop = calc.next;
                    fetchArrivalsForCurrentStop();
                }
            })
            .catch(function (err) {
                console.error('정류장 목록 조회 실패:', err);
                alert('정류장 정보를 가져오지 못했습니다.');
            });
    };

    $scope.searchStops = function () {
        if (!$scope.searchKeyword) {
            alert('정류장 이름을 입력하세요.');
            return;
        }
        var keyword = $scope.searchKeyword;
        cancelAutoRefresh();

        $scope.currentRouteId = null;
        $scope.representativeBus = null;
        $scope.prevStop = null;
        $scope.currentStop = null;
        $scope.nextStop = null;
        $scope.arrivalList = [];
        $scope.selectedStop = null;
        $scope.tempRouteIdFromStop = null;

        clearRouteLine();
        clearBusMarkers();
        clearRepPulse();
        lastRepVehicleNoForPan = null;
        hideHoverTooltip();

        $scope.isMapLoading = true;

        $http
            .get('/api/bus/stops-by-name', {
                params: { nodeName: keyword, pageNo: 1, numOfRows: 100 },
            })
            .then(function (res) {
                $scope.stopsResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    $scope.stops = [];
                    $scope.selectedStop = null;
                    return;
                }
                var itemsRoot = data.response.body.items;
                if (!itemsRoot || !itemsRoot.item) {
                    $scope.stops = [];
                    $scope.selectedStop = null;
                    alert('검색된 정류장이 없습니다.');
                    return;
                }
                var items = itemsRoot.item;
                var rawStopsArray = angular.isArray(items) ? items : [items];
                var stopsArray = rawStopsArray.map(function (s) {
                    var id = s.nodeid || s.nodeId || s.node_id || s.nodeno || s.sttnId || s.stationId;
                    return angular.extend({}, s, { nodeid: id });
                });
                $scope.stops = stopsArray;
                $scope.selectedStop = null;
                drawStopsOnMap(stopsArray);
            })
            .catch(function (err) {
                console.error('정류장 검색 실패:', err);
                alert('정류장 정보를 가져오지 못했습니다.');
            })
            .finally(function () {
                $scope.isMapLoading = false;
            });
    };

    $scope.fetchBusLocations = function () {
        if (!$scope.currentRouteId) return;
        $scope.isMapLoading = true;

        $http
            .get('/api/bus/locations', {
                params: { routeId: $scope.currentRouteId, pageNo: 1, numOfRows: 100 },
            })
            .then(function (res) {
                $scope.locationResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) {
                    clearBusMarkers();
                    $scope.representativeBus = null;
                    clearRepPulse();
                    return;
                }
                var items = data.response.body.items && data.response.body.items.item;
                if (!items) {
                    clearBusMarkers();
                    $scope.representativeBus = null;
                    clearRepPulse();
                    return;
                }
                var busArray = angular.isArray(items) ? items : [items];
                var newRepresentative = null;
                var oldRep = $scope.representativeBus;

                if (oldRep && oldRep.vehicleno) {
                    for (var i = 0; i < busArray.length; i++) {
                        var b = busArray[i];
                        if (b.vehicleno && b.vehicleno === oldRep.vehicleno) {
                            newRepresentative = b;
                            break;
                        }
                    }
                }
                if (!newRepresentative && busArray.length > 0) {
                    var idx = Math.floor(Math.random() * busArray.length);
                    newRepresentative = busArray[idx];
                }

                $scope.representativeBus = newRepresentative || null;

                if ($scope.representativeBus) {
                    panToRepresentativeBusIfNeeded($scope.representativeBus);
                    updateRepPulseFeatureByBus($scope.representativeBus);
                } else {
                    clearRepPulse();
                }

                if ($scope.representativeBus && $scope.stops && $scope.stops.length > 0) {
                    var calc2 = computePrevCurrentNextForBus($scope.representativeBus, $scope.stops);
                    $scope.prevStop = calc2.prev;
                    $scope.currentStop = calc2.current;
                    $scope.nextStop = calc2.next;
                    fetchArrivalsForCurrentStop();
                } else {
                    $scope.prevStop = null;
                    $scope.currentStop = null;
                    $scope.nextStop = null;
                    $scope.arrivalList = [];
                    $scope.selectedStop = null;
                }
                drawBusLocationsOnMap(busArray);
            })
            .catch(function (err) {
                console.error('버스 위치 조회 실패:', err);
                $scope.representativeBus = null;
                clearRepPulse();
            })
            .finally(function () {
                $scope.isMapLoading = false;
            });
    };

    // =========================================================
    // ★ [추가] 출발/도착 정류장 선택 기능 (하드코딩 제거용)
    // =========================================================
    $scope.setPathStart = function (stop) {
        $scope.pathStartStop = stop;
        $scope.pathTotalMinutes = null;
    };

    $scope.setPathEnd = function (stop) {
        $scope.pathEndStop = stop;
        $scope.pathTotalMinutes = null;
    };

    $scope.clearResultPath = function () {
        pathSource.clear();
        $scope.pathStartStop = null;
        $scope.pathEndStop = null;
        $scope.pathTotalMinutes = null;
    };

    // =========================================================
    // [수정됨] 2번 문제 최단 경로 검색 기능 (선택된 좌표 사용)
    // =========================================================
    $scope.solvePath = function () {
        // 유효성 검사: 사용자가 출발/도착지를 모두 선택했는지 확인
        if (!$scope.pathStartStop || !$scope.pathEndStop) {
            alert('먼저 목록에서 [출발] 정류장과 [도착] 정류장을 선택해주세요.');
            return;
        }

        // 선택된 객체에서 좌표 추출 (API마다 필드명이 다를 수 있어 방어적 코딩)
        var s = $scope.pathStartStop;
        var e = $scope.pathEndStop;

        var startLat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
        var startLng = parseFloat(s.gpslong || s.gpsLong || s.gpsX);
        var endLat = parseFloat(e.gpslati || e.gpsLati || e.gpsY);
        var endLng = parseFloat(e.gpslong || e.gpsLong || e.gpsX);

        if (isNaN(startLat) || isNaN(startLng) || isNaN(endLat) || isNaN(endLng)) {
            alert('선택한 정류장의 좌표 정보가 유효하지 않습니다.');
            return;
        }

        // 파라미터 구성
        var params = {
            fromLat: startLat,
            fromLng: startLng,
            toLat: endLat,
            toLng: endLng,
            snapRadiusM: 450, // 도보 스냅 반경 (450m)
        };

        $http
            .get('/api/path/solve', { params: params })
            .then(function (res) {
                var data = res.data;
                if (!data || !data.segments || data.segments.length === 0) {
                    alert('경로를 찾을 수 없습니다.\n(출발/도착지 450m 반경 내에 연결 가능한 버스가 없습니다)');
                    return;
                }

                var totalMin = Math.round(data.totalMinutes);
                console.log('경로 찾기 성공:', data);

                var sName = s.nodenm || s.stationName;
                var eName = e.nodenm || e.stationName;
                $scope.pathTotalMinutes = totalMin;

                // 경로 그리기 호출
                drawCalculatedPath(data.segments);
            })
            .catch(function (err) {
                console.error('경로 검색 오류:', err);
                alert('경로 계산 중 오류가 발생했습니다.');
            });
    };

    // [수정] 경로 그리기 함수 (화살표 추가 + Hover 데이터 심기 + 정류장 마커 추가)
    function drawCalculatedPath(segments) {
        if (!olMap) return;
        pathSource.clear(); // 기존 경로 삭제

        var extent = ol.extent.createEmpty(); // 화면 줌 맞춤용 범위
        var seenPathNodeKeys = {};
        // BUS 환승(노선 변경) 감지를 위한 상태값
        var prevBusRouteId = null;
        var busTransferIndex = -1; // 첫 BUS 구간에서 0이 되도록 -1로 시작
        // 중복 path_node 방지 (nodeId 기준)

        segments.forEach(function (seg, index) {
            if (!seg.points || seg.points.length < 2) return;

            // 1. 짧은 도보(건물 관통) 숨기기 로직
            // - 기존에는 seg.distance가 없어서(dist=0) "첫 WALK를 항상 숨김" 버그가 발생했다.
            // - 지금은 minutes 기준으로 "정말 의미 없는" 짧은 도보만 숨긴다.
            if (seg.mode === 'WALK') {
                var walkMin = seg.minutes != null ? parseFloat(seg.minutes) : null;
                // 0.5분(30초) 미만이면 지도상 표시를 생략 (필요 시 조절)
                if (walkMin != null && !isNaN(walkMin) && walkMin < 0.5) {
                    return;
                }
            }

            // 2. 좌표 변환 (WGS84 -> 지도 좌표계)
            var transformedCoords = seg.points.map(function (pt) {
                return lonLatToMapXY(pt[0], pt[1]);
            });

            // 3. 선(Line) 그리기
            var lineFeat = new ol.Feature({
                geometry: new ol.geom.LineString(transformedCoords),
            });

            // ★ Hover 데이터 저장 (분 단위) ★
            lineFeat.set('featureType', 'path_segment');
            lineFeat.set('mode', seg.mode); // WALK, BUS, TRAM
            // 백엔드에서 온 minutes 값을 정수로 반올림하여 저장
            lineFeat.set('minutes', seg.minutes ? Math.round(seg.minutes) : 0);
            lineFeat.set('routeId', seg.routeId);
            if (seg.mode === 'BUS' && seg.updowncd != null) {
                lineFeat.set('updowncd', parseInt(seg.updowncd, 10));
            }

            // BUS 구간은 노선(routeId)이 바뀌는 순간을 '환승'으로 보고 색 인덱스를 증가시킨다.
            if (seg.mode === 'BUS') {
                if (prevBusRouteId !== seg.routeId) {
                    busTransferIndex += 1;
                    prevBusRouteId = seg.routeId;
                }
                lineFeat.set('busTransferIndex', busTransferIndex);
            } else {
                // BUS가 아닌 구간은 색 인덱스를 0으로 둔다(스타일 함수에서 안전 처리)
                lineFeat.set('busTransferIndex', 0);
            }

            pathSource.addFeature(lineFeat);
            ol.extent.extend(extent, lineFeat.getGeometry().getExtent());

            // 4. 지나가는 정류장 마커(Node) 그리기
            // - Path API에서 nodeIds/nodeNames를 내려주므로, BUS/TRAM 구간의 모든 정류장(정거장)을 표시한다.
            if (seg.mode === 'BUS' || seg.mode === 'TRAM') {
                var circleBorderColor = seg.mode === 'TRAM' ? '#FF69B4' : '#0066ff';
                var nodeIds = seg.nodeIds || [];
                var nodeNames = seg.nodeNames || [];

                for (var ni = 0; ni < seg.points.length; ni++) {
                    var wgsPt = seg.points[ni];
                    if (!wgsPt || wgsPt.length < 2) continue;

                    var nodeId = nodeIds && nodeIds[ni] ? String(nodeIds[ni]) : null;
                    var nodeName = nodeNames && nodeNames[ni] ? String(nodeNames[ni]) : null;

                    var key = nodeId ? nodeId : wgsPt[0] + ',' + wgsPt[1];
                    if (seenPathNodeKeys[key]) continue;
                    seenPathNodeKeys[key] = true;

                    var mapPt = lonLatToMapXY(wgsPt[0], wgsPt[1]);
                    var nodeFeat = new ol.Feature({
                        geometry: new ol.geom.Point(mapPt),
                    });

                    nodeFeat.setStyle(
                        new ol.style.Style({
                            image: new ol.style.Circle({
                                radius: 5,
                                fill: new ol.style.Fill({ color: '#FFFFFF' }),
                                stroke: new ol.style.Stroke({ color: circleBorderColor, width: 2 }),
                            }),
                        })
                    );

                    nodeFeat.set('featureType', 'path_node');
                    nodeFeat.set('mode', seg.mode);
                    if (nodeId) nodeFeat.set('nodeId', nodeId);
                    if (nodeName) nodeFeat.set('nodeName', nodeName);
                    nodeFeat.set('wgsLat', parseFloat(wgsPt[1]));
                    nodeFeat.set('wgsLng', parseFloat(wgsPt[0]));

                    pathSource.addFeature(nodeFeat);
                }
            }

            // 5. 화살표(Arrow) 그리기 (도보 제외, 긴 구간만)
            if (seg.mode !== 'WALK' && transformedCoords.length > 3) {
                var arrowStep = 5;
                for (var i = 0; i < transformedCoords.length - 1; i += arrowStep) {
                    var p1 = transformedCoords[i];
                    var p2 = transformedCoords[i + 1];
                    var dx = p2[0] - p1[0];
                    var dy = p2[1] - p1[1];
                    var len = Math.sqrt(dx * dx + dy * dy);
                    if (len < 10) continue;

                    var angle = Math.atan2(dy, dx);
                    var mid = [(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2];

                    var arrowFeat = new ol.Feature({
                        geometry: new ol.geom.Point(mid),
                    });

                    var arrowColor = seg.mode === 'TRAM' ? '#FF1493' : '#0000FF';

                    // 화살표 스타일 (SVG 아이콘 사용 + 회전 보정)
                    arrowFeat.setStyle(
                        new ol.style.Style({
                            image: new ol.style.Icon({
                                src: buildRouteArrowSvgDataUri(arrowColor),
                                scale: 0.7,
                                rotation: -angle, // [수정됨] 단순하게 -angle로 변경
                                rotateWithView: true,
                            }),
                        })
                    );

                    pathSource.addFeature(arrowFeat);
                }
            }
        });

        // BUS 구간 routeId -> 버스번호(routenm) 매핑을 미리 로딩 (툴팁에서 routeId 대신 버스번호 표시)
        try {
            var routeIds = [];
            segments.forEach(function (seg) {
                if (!seg) return;
                if (seg.mode !== 'BUS') return;
                var rid = seg.routeId || null;
                if (!rid) return;
                routeIds.push(String(rid));
            });

            // 중복 제거
            var uniq = {};
            var uniqList = [];
            routeIds.forEach(function (rid) {
                if (uniq[rid]) return;
                uniq[rid] = true;
                uniqList.push(rid);
            });

            prefetchPathBusRouteNosByRouteIds(uniqList);
        } catch (e) {
            console.warn('최단경로 버스번호 매핑 프리패치 실패:', e);
        }

        // 6. 경로가 보이도록 지도 줌/이동
        if (!ol.extent.isEmpty(extent)) {
            olMap.getView().fit(extent, {
                padding: [50, 50, 50, 50],
                duration: 800,
            });
        }
    }

    // =========================================================
    // Collector 상태 (중복 제거하고 1벌만 유지)
    // =========================================================
    $scope.collectorStatus = { running: false, batchSize: 5, intervalMs: 5000, lastElapsedMs: 0, inProgress: false };
    $scope.collectorStatusText = '상태: OFF';

    var collectorPoll = null;

    function startCollectorPoll() {
        if (collectorPoll) return;
        collectorPoll = $interval(function () {
            refreshCollectorStatus();
        }, 10000);
    }

    function stopCollectorPoll() {
        if (!collectorPoll) return;
        $interval.cancel(collectorPoll);
        collectorPoll = null;
    }

    function applyCollectorStatus(d) {
        $scope.collectorStatus = d;

        if (d && d.running) {
            $scope.collectorStatusText = '상태: ON · batch ' + d.batchSize + (d.inProgress ? ' · 실행중' : '');
            startCollectorPoll();
        } else {
            $scope.collectorStatusText = '상태: OFF';
            stopCollectorPoll();
        }
    }

    function refreshCollectorStatus() {
        $http.get('/collector/status').then(
            function (res) {
                var d = res && res.data ? res.data : null;
                if (!d) return;
                applyCollectorStatus(d);
            },
            function (err) {
                $scope.collectorStatusText = '상태 조회 실패: ' + (err && err.status != null ? err.status : 'UNKNOWN');
            }
        );
    }

    $scope.toggleCollector = function () {
        $http.get('/collector/toggle').then(
            function (res) {
                var d = res && res.data ? res.data : null;
                if (d) applyCollectorStatus(d);
                refreshCollectorStatus();
            },
            function (err) {
                $scope.collectorStatusText = '토글 실패: ' + (err && err.status != null ? err.status : 'UNKNOWN');
            }
        );
    };

    // 페이지 진입 시: 상태 1회 확인(ON이면 applyCollectorStatus가 폴링 시작)
    refreshCollectorStatus();
});
