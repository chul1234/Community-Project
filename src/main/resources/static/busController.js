// 수정됨: 정류장 목록 클릭 시 해당 정류장 위치로 '줌인 + 이동' 기능 추가
//       + 기존 디자인(SVG 버스, 마커 강조 등) 모두 유지

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
    const CITY_CODE = '25';

    $scope.searchType    = 'route';
    $scope.searchKeyword = '';
    $scope.searchTerm    = '';

    $scope.map1 = null;
    var olMap = null;

    $scope.routeResultJson    = '';
    $scope.stopsResultJson    = '';
    $scope.locationResultJson = '';

    $scope.currentRouteId = null;

    $scope.stops = [];
    $scope.selectedStop = null;

    var autoRefreshPromise = null;
    $scope.isAutoRefreshOn = false;

    $scope.isMapLoading = false;

    $scope.representativeBus = null;

    $scope.prevStop    = null;
    $scope.currentStop = null;
    $scope.nextStop    = null;

    $scope.arrivalList = [];

    var lastArrivalDrawRequestId = 0;

    // 정류장 검색 모드에서 “버스 클릭 → 라인 표시”용 임시 routeId
    $scope.tempRouteIdFromStop = null;

    // =========================================================
    // [디자인] SVG 아이콘 생성 헬퍼 함수
    // =========================================================
    function createSvgIcon(color, type) {
        var svg = '';
        // 버스 아이콘 (앞모습)
        if (type === 'bus') {
            svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">' +
                  '<path fill="' + color + '" d="M48 64C48 28.7 76.7 0 112 0H400c35.3 0 64 28.7 64 64V448c0 35.3-28.7 64-64 64H384c-17.7 0-32-14.3-32-32s14.3-32 32-32h16c8.8 0 16-7.2 16-16V384H96v64c0 8.8 7.2 16 16 16h16c17.7 0 32 14.3 32 32s-14.3 32-32 32H112c-35.3 0-64-28.7-64-64V64zm32 32c0-17.7 14.3-32 32-32H400c17.7 0 32 14.3 32 32v64c0 17.7-14.3 32-32 32H112c-17.7 0-32-14.3-32-32V96zm0 160c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H80zm352 0c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H432z"/>' +
                  '</svg>';
        }
        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    }

    // -------------------------
    // OpenLayers 벡터 레이어 준비 (정류장/버스)
    // -------------------------
    var stopSource = new ol.source.Vector();
    var stopLayer  = new ol.layer.Vector({
        source: stopSource,
        zIndex: 10 // 정류장은 버스보다 아래
    });

    var busSource = new ol.source.Vector();
    var busLayer  = new ol.layer.Vector({
        source: busSource,
        zIndex: 20 // 버스는 정류장보다 위
    });

    // -------------------------
    // [디자인] 노선 라인 레이어 (부드러운 파란색)
    // -------------------------
    var routeLineSource = new ol.source.Vector();
    var routeLineLayer  = new ol.layer.Vector({
        source: routeLineSource,
        zIndex: 5, // 가장 아래
        style: new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: 'rgba(0, 102, 255, 0.7)', // 약간 투명하게
                width: 5,
                lineCap: 'round', // 선 끝 둥글게
                lineJoin: 'round' // 꺾임 둥글게
            })
        })
    });

    // -------------------------
    // 노선 라인 방향 화살표
    // -------------------------
    var ROUTE_ARROW_EVERY_N_SEGMENTS = 2;
    var ROUTE_ARROW_MIN_SEGMENT_LEN  = 30;
    var routeArrowStyleCache         = {};

    function buildRouteArrowSvgDataUri(fillColor) {
        var svg =
            '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' +
            '<path fill="' + fillColor + '" d="M4 12h11.2l-3.6-3.6L13 7l7 7-7 7-1.4-1.4 3.6-3.6H4z"/>' +
            '</svg>';

        return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
    }

    function getRouteArrowStyle(rotationRad) {
        var key = (Math.round(rotationRad * 100) / 100).toString();
        if (routeArrowStyleCache[key]) return routeArrowStyleCache[key];

        routeArrowStyleCache[key] = new ol.style.Style({
            image: new ol.style.Icon({
                src: buildRouteArrowSvgDataUri('#0066ff'),
                rotateWithView: true,
                rotation: rotationRad,
                scale: 0.7,
                opacity: 0.95
            })
        });

        return routeArrowStyleCache[key];
    }

    // -------------------------
    // 툴팁 Overlay (Hover)
    // -------------------------
    var hoverTooltipEl = null;
    var hoverTooltipOverlay = null;

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
            stopEvent: false
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
            var isStopSearchMode = !isRouteMode && ($scope.stops && $scope.stops.length > 0);

            if (!isRouteMode && !isStopSearchMode) {
                hideHoverTooltip();
                return;
            }

            var pixel = olMap.getEventPixel(evt.originalEvent);

            var feature = olMap.forEachFeatureAtPixel(
                pixel,
                function (f) { return f; },
                {
                    layerFilter: function (layer) {
                        return layer !== repPulseLayer; // 펄스 효과는 호버 제외
                    }
                }
            );

            if (!feature) {
                hideHoverTooltip();
                return;
            }

            var fType = feature.get('featureType');

            // 정류장 호버
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

            // 버스 호버
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
    // [디자인] 대표 버스 펄스(파동) 애니메이션 레이어
    // -------------------------
    var repPulseSource = new ol.source.Vector();
    var repPulseLayer  = new ol.layer.Vector({
        source: repPulseSource,
        zIndex: 15, // 버스 아이콘 밑, 라인 위
        style: function () {
            if (!$scope.representativeBus) return null;
            if (!$scope.currentRouteId) return null;

            var t = Date.now();
            var phase = (t % 1500) / 1500.0; // 속도 조절
            var radius = 5 + (phase * 20); // 크기 축소 반영
            var opacity = 1.0 - phase;

            // 주황색 계열로 펄스 색상 변경
            var pulseColor = '255, 149, 0'; 

            return new ol.style.Style({
                image: new ol.style.Circle({
                    radius: radius,
                    stroke: new ol.style.Stroke({
                        color: 'rgba(' + pulseColor + ', ' + opacity.toFixed(3) + ')',
                        width: 2 + (2 * (1 - phase))
                    }),
                    fill: new ol.style.Fill({
                        color: 'rgba(' + pulseColor + ', ' + (opacity * 0.1).toFixed(3) + ')'
                    })
                })
            });
        }
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

        var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');

        if (!repPulseFeature) {
            repPulseFeature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179)
            });
            repPulseSource.addFeature(repPulseFeature);
        } else {
            repPulseFeature.setGeometry(new ol.geom.Point(xy5179));
        }
        startRepPulseAnimationLoop();
    }

    // -------------------------
    // 대표 버스로 지도 이동 (부드러운 이동)
    // -------------------------
    var lastRepVehicleNoForPan = null;
    var lastRepPanAtMs = 0;
    var REP_ZOOM_IN_DELTA = 1;
    var REP_ZOOM_MAX      = 15;

    function panToRepresentativeBusIfNeeded(bus) {
        if (!olMap) return;
        if (!bus) return;
        if (!$scope.currentRouteId) return;

        var vehicleno = (bus.vehicleno != null) ? String(bus.vehicleno) : null;
        if (!vehicleno) return;

        if (lastRepVehicleNoForPan === vehicleno) return;

        var now = Date.now();
        if (now - lastRepPanAtMs < 1000) return;

        var lat = parseFloat(bus.gpslati);
        var lon = parseFloat(bus.gpslong);
        if (isNaN(lat) || isNaN(lon)) return;

        var center5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
        var view = olMap.getView();
        if (!view) return;

        var currentZoom = view.getZoom();
        var targetZoom = currentZoom;
        if (typeof currentZoom === 'number') {
            targetZoom = Math.min(REP_ZOOM_MAX, currentZoom + REP_ZOOM_IN_DELTA);
        }

        view.animate(
            { center: center5179, duration: 800 },
            { zoom: targetZoom, duration: 800 }
        );

        lastRepVehicleNoForPan = vehicleno;
        lastRepPanAtMs = now;
    }

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
    // 정류장 검색 모드: 버스 클릭 → 노선 라인만 표시
    // -------------------------
    function initBusClickToShowRouteLine() {
        if (!olMap) return;
        if (olMap.__busClickToRouteLineBound) return;

        olMap.__busClickToRouteLineBound = true;

        olMap.on('singleclick', function (evt) {
            if (!olMap) return;

            // 정류장 검색 모드에서만 동작
            var isRouteMode = !!$scope.currentRouteId;
            var isStopSearchMode = !isRouteMode && ($scope.stops && $scope.stops.length > 0);
            if (!isStopSearchMode) return;

            var pixel = olMap.getEventPixel(evt.originalEvent);

            var feature = olMap.forEachFeatureAtPixel(
                pixel,
                function (f) { return f; },
                {
                    layerFilter: function (layer) {
                        return layer !== repPulseLayer;
                    }
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

            // currentRouteId는 건드리지 않고 임시 routeId만 세팅
            $scope.tempRouteIdFromStop = String(routeId);

            clearRouteLine();

            $http.get('/api/bus/route-stops', {
                params: { routeId: routeId }
            }).then(function (res) {
                var data = parseMaybeJson(res.data);
                if (!data || !data.response || !data.response.body) return;

                var items = data.response.body.items && data.response.body.items.item;
                if (!items) return;

                var stopsArray = angular.isArray(items) ? items : [items];
                drawRouteLineFromStops(stopsArray);

                if (!$scope.$$phase) $scope.$applyAsync();
            }).catch(function (err) {
                console.error('버스 클릭 → 노선 정류장 조회 실패:', err);
            });
        });
    }

    // -------------------------
    // 지도 초기화
    // -------------------------
    $scope.initMap = function () {
        var mapDiv = document.getElementById('map1');

        if (!window.ngii_wmts || !mapDiv) {
            console.error('NGII 지도 스크립트 미로드');
            return;
        }

        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 3
        });

        if (typeof $scope.map1._getMap === 'function') {
            olMap = $scope.map1._getMap();
        } else {
            console.warn('_getMap 함수 없음');
            olMap = null;
        }

        if (olMap && typeof olMap.addLayer === 'function') {
            olMap.addLayer(routeLineLayer);
            olMap.addLayer(stopLayer);
            olMap.addLayer(busLayer);
            olMap.addLayer(repPulseLayer);
            console.log('레이어 추가 완료 (디자인 적용됨)');
        }

        initHoverTooltip();
        initBusClickToShowRouteLine();
    };

    $timeout($scope.initMap, 0);

    // -------------------------
    // 노선 라인 그리기
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
                var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
                coordinates.push(xy5179);
            }
        });

        if (coordinates.length < 2) return;

        var lineFeature = new ol.Feature({
            geometry: new ol.geom.LineString(coordinates)
        });
        routeLineSource.addFeature(lineFeature);

        // 화살표 그리기
        for (var i = 0; i < coordinates.length - 1; i++) {
            if (ROUTE_ARROW_EVERY_N_SEGMENTS > 1 && (i % ROUTE_ARROW_EVERY_N_SEGMENTS) !== 0) continue;

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
                geometry: new ol.geom.Point(mid)
            });
            arrowFeature.setStyle(getRouteArrowStyle(angle));
            routeLineSource.addFeature(arrowFeature);
        }

        // 라인 범위로 줌 이동
        var extent = routeLineSource.getExtent();
        if (extent && isFinite(extent[0])) {
            var view = olMap.getView();
            if (view) {
                view.fit(extent, {
                    padding: [60, 60, 60, 60],
                    maxZoom: 14,
                    duration: 500
                });
            }
        }
    }

    // -------------------------
    // [디자인] 정류장 마커 (선택 시 파란색 강조)
    // -------------------------
    function clearStopMarkers() {
        var newSrc = new ol.source.Vector();
        stopLayer.setSource(newSrc);
        stopSource = newSrc;
    }

    function addStopMarkerToSource(targetSource, lat, lon, title, stopData, isSelected) {
        if (!olMap) return;
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
                name: title || ''
            });

            feature.set('featureType', 'stop');
            feature.set('stopData', stopData || null);

            // [디자인] 선택 여부에 따른 스타일 분기
            var fillColor   = isSelected ? '#007bff' : '#ffffff';
            var strokeColor = isSelected ? '#ffffff' : '#555555';
            var strokeWidth = isSelected ? 3 : 2;
            var radiusVal   = isSelected ? 8 : 5; 
            var zIndexVal   = isSelected ? 999 : 10;

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: radiusVal,
                        fill: new ol.style.Fill({ color: fillColor }),
                        stroke: new ol.style.Stroke({ color: strokeColor, width: strokeWidth })
                    }),
                    zIndex: zIndexVal
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
                duration: 500
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
            
            // 선택된 정류장인지 확인
            var isSelected = ($scope.selectedStop && s === $scope.selectedStop);

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
    // [디자인] 버스 마커 (SVG 아이콘 사용)
    // -------------------------
    function clearBusMarkers() {
        var newSrc = new ol.source.Vector();
        busLayer.setSource(newSrc);
        busSource = newSrc;
    }

    function addBusMarkerToSource(targetSource, lat, lon, title, isRepresentative, busData) {
        if (!olMap) return;
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
                name: title || ''
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
                        rotation: 0 
                    }),
                    zIndex: zIndexVal
                })
            ];

            if (busNoText) {
                styleArray.push(new ol.style.Style({
                    text: new ol.style.Text({
                        text: busNoText,
                        font: 'bold 12px "Pretendard", sans-serif',
                        fill: new ol.style.Fill({ color: '#333' }),
                        stroke: new ol.style.Stroke({ color: '#fff', width: 3 }),
                        offsetY: -15,
                        textAlign: 'center'
                    }),
                    zIndex: zIndexVal + 1
                }));
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
                    isRepresentative = (rep.vehicleno === b.vehicleno);
                }
                addBusMarkerToSource(newSrc, lat, lon, String(label).trim(), isRepresentative, b);
            }
        });

        busLayer.setSource(newSrc);
        busSource = newSrc;
    }

    // -------------------------
    // API 호출 및 로직
    // -------------------------
    function computePrevCurrentNextForBus(bus, stops) {
        var result = { prev: null, current: null, next: null };
        if (!bus || !stops || !stops.length) return result;

        var currentIndex = -1;
        var busNodeId    = bus.nodeid || bus.nodeId || null;
        var busSeq       = bus.routeseq || bus.routeSeq || null;

        if (busNodeId) {
            for (var i = 0; i < stops.length; i++) {
                var s = stops[i];
                if ((s.nodeid || s.nodeId) === busNodeId) { currentIndex = i; break; }
            }
        }

        if (currentIndex === -1 && busSeq != null) {
            var busSeqNum = parseInt(busSeq, 10);
            if (!isNaN(busSeqNum)) {
                for (var j = 0; j < stops.length; j++) {
                    var st = stops[j];
                    var stopSeq = parseInt(st.routeseq || st.routeSeq, 10);
                    if (!isNaN(stopSeq) && stopSeq === busSeqNum) { currentIndex = j; break; }
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
            $http.get('/api/bus/locations', {
                params: { routeId: rid, pageNo: 1, numOfRows: 100 }
            }).then(function (res) {
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
                    var label = (b.routenm != null) ? String(b.routenm) : '';
                    addBusMarkerToSource(tempSource, lat, lon, String(label).trim(), false, b);
                });
            }).catch(function (err) {
                console.error('정류장 모드 버스 위치 조회 실패:', err);
            }).finally(function () {
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

        $http.get('/api/bus/arrivals', {
            params: { nodeId: nodeId, numOfRows: 20 }
        }).then(function (res) {
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
                var remainStops = (a.arrprevstationcnt != null) ? parseInt(a.arrprevstationcnt, 10) : null;
                var sec = (a.arrtime != null) ? parseInt(a.arrtime, 10) : null;
                var minutes = null;
                if (!isNaN(sec) && sec != null) minutes = Math.round(sec / 60.0);
                return angular.extend({}, a, {
                    remainStops: isNaN(remainStops) ? null : remainStops,
                    remainMinutes: minutes
                });
            });
            $scope.arrivalList = mapped;
            drawBusesForArrivalRoutes($scope.arrivalList);
        }).catch(function (err) {
            console.error('도착 정보 조회 실패:', err);
            $scope.arrivalList = previousArrivalList;
        });
    }

    // [수정됨] 정류장 선택 시 -> 색상 강조 + 줌인/이동
    $scope.selectStop = function (stop) {
        if (!stop) return;
        $scope.selectedStop = stop;
        $scope.currentStop  = stop;

        fetchArrivalsForCurrentStop();
        
        // 1. 마커 색상 갱신
        drawStopsOnMap($scope.stops);

        // 2. [추가됨] 해당 정류장 위치로 지도 이동 및 줌인
        if (olMap) {
            var lat = parseFloat(stop.gpslati || stop.gpsLati || stop.gpsY);
            var lon = parseFloat(stop.gpslong || stop.gpsLong || stop.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                var center = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
                var view = olMap.getView();
                if (view) {
                    view.animate({
                        center: center,
                        zoom: 17, // 확대 레벨 (가까이)
                        duration: 500
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

        $http.get('/api/bus/routes', { params: { routeNo: routeNo } })
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
            }).catch(function (err) {
                console.error('노선 조회 실패:', err);
                alert('노선 정보를 가져오지 못했습니다.');
            });
    };

    $scope.fetchRouteStops = function (routeId) {
        if (!routeId) return;
        $http.get('/api/bus/route-stops', { params: { routeId: routeId } })
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
            }).catch(function (err) {
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

        $http.get('/api/bus/stops-by-name', {
            params: { nodeName: keyword, pageNo: 1, numOfRows: 100 }
        }).then(function (res) {
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
        }).catch(function (err) {
            console.error('정류장 검색 실패:', err);
            alert('정류장 정보를 가져오지 못했습니다.');
        }).finally(function () {
            $scope.isMapLoading = false;
        });
    };

    $scope.fetchBusLocations = function () {
        if (!$scope.currentRouteId) return;
        $scope.isMapLoading = true;

        $http.get('/api/bus/locations', {
            params: { routeId: $scope.currentRouteId, pageNo: 1, numOfRows: 100 }
        }).then(function (res) {
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
        }).catch(function (err) {
            console.error('버스 위치 조회 실패:', err);
            $scope.representativeBus = null;
            clearRepPulse();
        }).finally(function () {
            $scope.isMapLoading = false;
        });
    };
});