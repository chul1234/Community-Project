// 수정됨: 7번(hover) 2단계 추가 - 정류장 검색 모드에서도 hover 툴팁 동작
//       - 노선(버스) 모드: 기존처럼 버스=다음 정류장 포함, 정류장=이름
//       - 정류장 검색 모드: 정류장=이름, 버스=노선/차량만 표시(노선별 정류장 목록이 없어서 다음 정류장 계산 불가)

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
    // 노선 라인 레이어 (3번)
    // -------------------------
    var routeLineSource = new ol.source.Vector();
    var routeLineLayer  = new ol.layer.Vector({
        source: routeLineSource,
        style: new ol.style.Style({
            stroke: new ol.style.Stroke({
                color: '#0066ff',
                width: 4
            })
        })
    });

    // -------------------------
    // 노선 라인 방향 화살표(4번)
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
    // 7번(hover) - OpenLayers 툴팁 Overlay
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
        hoverTooltipEl.style.background = 'rgba(0, 0, 0, 0.75)';
        hoverTooltipEl.style.color = '#ffffff';
        hoverTooltipEl.style.padding = '6px 8px';
        hoverTooltipEl.style.borderRadius = '6px';
        hoverTooltipEl.style.fontSize = '12px';
        hoverTooltipEl.style.whiteSpace = 'nowrap';
        hoverTooltipEl.style.display = 'none';
        hoverTooltipEl.style.zIndex = '9999';

        mapDiv.appendChild(hoverTooltipEl);

        hoverTooltipOverlay = new ol.Overlay({
            element: hoverTooltipEl,
            offset: [12, 0],
            positioning: 'bottom-left',
            stopEvent: false
        });

        olMap.addOverlay(hoverTooltipOverlay);

        // ✅ 2단계: 지도 영역에서 마우스가 나가면 툴팁 숨김(툴팁 “고정” 방지)
        mapDiv.addEventListener('mouseleave', function () {
            hideHoverTooltip();
        });

        olMap.on('pointermove', function (evt) {
            // ✅ 드래그 중엔 무조건 숨김
            if (evt.dragging) {
                hideHoverTooltip();
                return;
            }

            // ✅ 2단계: 노선 모드 OR 정류장 검색 모드일 때만 hover 활성화
            var isRouteMode = !!$scope.currentRouteId;
            var isStopSearchMode = !isRouteMode && ($scope.stops && $scope.stops.length > 0);

            if (!isRouteMode && !isStopSearchMode) {
                hideHoverTooltip();
                return;
            }

            var pixel = olMap.getEventPixel(evt.originalEvent);

            // ✅ repPulseLayer(대표 펄스) hit-detect 제외
            var feature = olMap.forEachFeatureAtPixel(
                pixel,
                function (f) { return f; },
                {
                    layerFilter: function (layer) {
                        return layer !== repPulseLayer;
                    }
                }
            );

            if (!feature) {
                hideHoverTooltip();
                return;
            }

            var fType = feature.get('featureType');

            if (fType === 'stop') {
                var stopData = feature.get('stopData') || null;
                var stopName =
                    (stopData && (stopData.nodenm || stopData.stationName)) ||
                    feature.get('name') ||
                    '';

                if (!stopName) {
                    hideHoverTooltip();
                    return;
                }

                showHoverTooltip(evt.coordinate, stopName);
                return;
            }

            if (fType === 'bus') {
                var busData = feature.get('busData') || null;
                if (!busData) {
                    hideHoverTooltip();
                    return;
                }

                // 노선번호(예: 102)
                var routeNo =
                    (busData.routenm != null ? String(busData.routenm) : '') ||
                    (busData.routeno != null ? String(busData.routeno) : '') ||
                    '';

                var vehicleNo =
                    (busData.vehicleno != null ? String(busData.vehicleno) : '') ||
                    '';

                var parts = [];
                if (routeNo) parts.push('노선: ' + routeNo);
                if (vehicleNo) parts.push('차량: ' + vehicleNo);

                // ✅ 노선(버스) 모드에서만 “다음 정류장” 계산 가능
                if (isRouteMode) {
                    var calc = computePrevCurrentNextForBus(busData, $scope.stops || []);
                    var nextStopName =
                        (calc && calc.next && (calc.next.nodenm || calc.next.stationName)) ||
                        '';
                    if (nextStopName) parts.push('다음: ' + nextStopName);
                }

                var text = parts.join(' / ');
                if (!text) {
                    hideHoverTooltip();
                    return;
                }

                showHoverTooltip(evt.coordinate, text);
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
    // 6번: 대표 버스 펄스(파동) 애니메이션 레이어
    // -------------------------
    var repPulseSource = new ol.source.Vector();
    var repPulseLayer  = new ol.layer.Vector({
        source: repPulseSource,
        style: function () {
            if (!$scope.representativeBus) return null;
            if (!$scope.currentRouteId) return null;

            var t = Date.now();
            var phase = (t % 900) / 900.0;
            var radius = 10 + (phase * 10);
            var opacity = 1.0 - phase;

            return new ol.style.Style({
                image: new ol.style.Circle({
                    radius: radius,
                    stroke: new ol.style.Stroke({
                        color: 'rgba(255, 212, 0, ' + opacity.toFixed(3) + ')',
                        width: 3
                    }),
                    fill: new ol.style.Fill({
                        color: 'rgba(255, 212, 0, ' + (opacity * 0.15).toFixed(3) + ')'
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
    // 2번: 대표 버스로 지도 이동 + 살짝 줌인 (대표 변경될 때만)
    // -------------------------
    var lastRepVehicleNoForPan = null;
    var lastRepPanAtMs = 0;

    // 대표 선정 시 줌인 설정
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
            { center: center5179, duration: 700 },
            { zoom: targetZoom, duration: 700 }
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
            zoom: 3
        });

        if (typeof $scope.map1._getMap === 'function') {
            olMap = $scope.map1._getMap();
        } else {
            console.warn('_getMap 함수를 찾을 수 없습니다. NGII 스크립트 버전 확인 필요.');
            olMap = null;
        }

        if (olMap && typeof olMap.addLayer === 'function') {
            // 라인 -> 정류장 -> 버스 -> 대표펄스(맨 위)
            olMap.addLayer(routeLineLayer);
            olMap.addLayer(stopLayer);
            olMap.addLayer(busLayer);
            olMap.addLayer(repPulseLayer);

            console.log('벡터 레이어(노선 라인/정류장/버스/대표펄스)를 ol.Map 에 추가 완료.');
        } else {
            console.warn('ol.Map 인스턴스 또는 addLayer 를 찾지 못했습니다. 마커는 표시되지 않을 수 있습니다.');
        }

        initHoverTooltip();

        console.log('NGII map 초기화 완료:', $scope.map1, olMap);
    };

    $timeout($scope.initMap, 0);

    // -------------------------
    // 노선 라인 (3번 + 4번)
    // -------------------------
    function clearRouteLine() {
        routeLineSource.clear();
    }

    function drawRouteLineFromStops(stops) {
        if (!$scope.currentRouteId) {
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

        for (var i = 0; i < coordinates.length - 1; i++) {
            if (ROUTE_ARROW_EVERY_N_SEGMENTS > 1 && (i % ROUTE_ARROW_EVERY_N_SEGMENTS) !== 0) {
                continue;
            }

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
    // 정류장 마커
    // -------------------------
    function clearStopMarkers() {
        var newSrc = new ol.source.Vector();
        stopLayer.setSource(newSrc);
        stopSource = newSrc;
    }

    function addStopMarkerToSource(targetSource, lat, lon, title, stopData) {
        if (!olMap) return;
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');

            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
                name: title || ''
            });

            // ✅ hover용 메타데이터
            feature.set('featureType', 'stop');
            feature.set('stopData', stopData || null);

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 4,
                        fill: new ol.style.Fill({ color: '#ff0000' }),
                        stroke: new ol.style.Stroke({ color: '#ffffff', width: 1 })
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
        if (!extent || !isFinite(extent[0]) || !isFinite(extent[1]) || !isFinite(extent[2]) || !isFinite(extent[3])) return;

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

        var newSrc = new ol.source.Vector();

        stops.forEach(function (s) {
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY);
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                addStopMarkerToSource(newSrc, lat, lon, s.nodenm || s.stationName || '', s);
            }
        });

        stopLayer.setSource(newSrc);
        stopSource = newSrc;

        fitMapToStops();
    }

    // -------------------------
    // 버스 마커
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

            // ✅ hover용 메타데이터
            feature.set('featureType', 'bus');
            feature.set('busData', busData || null);

            var fillColor   = isRepresentative ? '#ffd400' : '#0000ff';
            var radiusValue = isRepresentative ? 7 : 5;

            // 정류장 모드에서만 노선번호 텍스트 표시
            var busNoText = '';
            if (!$scope.currentRouteId && title != null) {
                busNoText = String(title).trim();
            }

            feature.setStyle(
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: radiusValue,
                        fill: new ol.style.Fill({ color: fillColor }),
                        stroke: new ol.style.Stroke({ color: '#ffffff', width: 1 })
                    }),
                    text: (busNoText
                        ? new ol.style.Text({
                              text: busNoText,
                              font: 'bold 10px sans-serif',
                              fill: new ol.style.Fill({ color: '#222222' }),
                              stroke: new ol.style.Stroke({ color: '#ffffff', width: 3 }),
                              offsetY: -12,
                              textAlign: 'center'
                          })
                        : null)
                })
            );

            targetSource.addFeature(feature);
        } catch (e) {
            console.warn('버스 마커 생성/좌표 변환 오류:', e);
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
    // 대표 버스의 이전/현재/다음 정류장 계산
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

    // -------------------------
    // [정류장 모드] 도착정보 기반 버스 위치 전부 표시
    // -------------------------
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
                if (!data || !data.response || !data.response.body) {
                    console.warn('정류장 모드 버스 위치 응답 구조가 예상과 다름(routeId=' + rid + '):', data);
                    return;
                }

                var items = data.response.body.items && data.response.body.items.item;
                if (!items) return;

                var busArray = angular.isArray(items) ? items : [items];

                busArray.forEach(function (b) {
                    var lat = parseFloat(b.gpslati);
                    var lon = parseFloat(b.gpslong);
                    if (isNaN(lat) || isNaN(lon)) return;

                    var label = (b.routenm != null) ? String(b.routenm) : '';
                    addBusMarkerToSource(tempSource, lat, lon, String(label).trim(), false, b);
                });
            }).catch(function (err) {
                console.error('정류장 모드 버스 위치 조회 실패(routeId=' + rid + '):', err);
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

    // -------------------------
    // 현재 정류장 기준 도착 예정 버스 조회
    // -------------------------
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
            console.error('도착 예정 버스 조회 실패:', err);
            $scope.arrivalList = previousArrivalList;
        });
    }

    // -------------------------
    // 정류장 목록 클릭 시
    // -------------------------
    $scope.selectStop = function (stop) {
        if (!stop) return;

        $scope.selectedStop = stop;
        $scope.currentStop  = stop;

        fetchArrivalsForCurrentStop();
    };

    // -------------------------
    // 자동 갱신
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

    // =========================
    // 공통 검색
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
            $scope.routeResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);

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

            lastRepVehicleNoForPan = null;

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
            $scope.stopsResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);

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

            drawStopsOnMap(stopsArray);
            drawRouteLineFromStops(stopsArray);

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

        $scope.currentRouteId    = null;
        $scope.representativeBus = null;
        $scope.prevStop          = null;
        $scope.currentStop       = null;
        $scope.nextStop          = null;
        $scope.arrivalList       = [];
        $scope.selectedStop      = null;

        clearRouteLine();
        clearBusMarkers();

        clearRepPulse();
        lastRepVehicleNoForPan = null;

        // 정류장 모드로 들어오면 hover 툴팁 숨김(1단계는 노선모드만)
        hideHoverTooltip();

        $scope.isMapLoading = true;

        $http.get('/api/bus/stops-by-name', {
            params: { nodeName: keyword, pageNo: 1, numOfRows: 100 }
        }).then(function (res) {
            $scope.stopsResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);

            var data = parseMaybeJson(res.data);
            if (!data || !data.response || !data.response.body) {
                console.warn('정류장 검색 응답 구조가 예상과 다름:', data);
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
                var id =
                    s.nodeid ||
                    s.nodeId ||
                    s.node_id ||
                    s.nodeno ||
                    s.sttnId ||
                    s.stationId;

                return angular.extend({}, s, { nodeid: id });
            });

            $scope.stops = stopsArray;
            $scope.selectedStop = null;

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
            params: { routeId: $scope.currentRouteId, pageNo: 1, numOfRows: 100 }
        }).then(function (res) {
            $scope.locationResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2);

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

                clearRepPulse();
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

            // ✅ 2번/6번: 대표 버스로 이동 + 줌인 + 펄스
            if ($scope.representativeBus) {
                panToRepresentativeBusIfNeeded($scope.representativeBus);
                updateRepPulseFeatureByBus($scope.representativeBus);
            } else {
                clearRepPulse();
            }

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

            clearRepPulse();
        }).finally(function () {
            $scope.isMapLoading = false;
        });
    };
});

// 수정됨 끝
