// 수정됨: 트램 라인(section별 세그먼트 분리) 렌더링 시 구간 경계에서 라인이 끊기는 문제를 해결하기 위해, 섹션 변경 시 "이전 세그먼트의 마지막 점"을 "다음 세그먼트의 시작점"으로 함께 포함하여 연결되도록 수정

// =========================
// 좌표계 정의 (UTM-K, GRS80)
// =========================
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

    // =========================================================
    // [트램] 토글 상태 (HTML 버튼과 바인딩: isTramVisible)
    // =========================================================
    $scope.isTramVisible = false; // ✅ 초기엔 "보이기" 상태 (지금 바로 보이면 안 됨)

    // =========================================================
    // [트램] 구간별 색상 매핑 (이미지처럼 #AB3937 / #202020 적용)
    //  - section 이름('1구간', '2구간'...) 기준으로 라인 색을 분리한다.
    //  - 매핑이 없는 section은 기본 #202020을 사용한다.
    // =========================================================
    var TRAM_SECTION_COLOR_MAP = {
        // 트램 구간별 색상 테이블
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
        // section -> 색상 반환
        if (!sectionName) return '#202020'; // 기본색
        return TRAM_SECTION_COLOR_MAP[sectionName] || '#202020'; // 매핑 없으면 기본색
    }

    // 트램 라인 스타일 캐시 (섹션 색상별)
    var tramLineStyleCache = {}; // { '#95443E': Style, '#202020': Style ... }

    function getTramLineStyleByColor(hexColor) {
        // 라인 스타일(섹션별) 반환
        var key = String(hexColor || '#202020'); // 캐시 키
        if (tramLineStyleCache[key]) return tramLineStyleCache[key]; // 있으면 반환

        tramLineStyleCache[key] = new ol.style.Style({
            // 새 스타일 생성
            stroke: new ol.style.Stroke({
                color: key, // ✅ 섹션 색상 그대로 사용
                width: 6, // 두께
                lineCap: 'round',
                lineJoin: 'round',
            }),
        });

        return tramLineStyleCache[key]; // 반환
    }

    // =========================================================
    // [디자인] SVG 아이콘 생성 함수
    // =========================================================
    function createSvgIcon(color, type) {
        var svg = ''; // SVG 문자열 초기화
        // 버스 아이콘일 경우
        if (type === 'bus') {
            svg =
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">' +
                '<path fill="' +
                color +
                '" d="M48 64C48 28.7 76.7 0 112 0H400c35.3 0 64 28.7 64 64V448c0 35.3-28.7 64-64 64H384c-17.7 0-32-14.3-32-32s14.3-32 32-32h16c8.8 0 16-7.2 16-16V384H96v64c0 8.8 7.2 16 16 16h16c17.7 0 32 14.3 32 32s-14.3 32-32 32H112c-35.3 0-64-28.7-64-64V64zm32 32c0-17.7 14.3-32 32-32H400c17.7 0 32 14.3 32 32v64c0 17.7-14.3 32-32 32H112c-17.7 0-32-14.3-32-32V96zm0 160c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H80zm352 0c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H432z"/>' +
                '</svg>';
        }
        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg); // Data URI 반환
    }

    // =========================================================
    // [트램] 라인/정거장 레이어 (버스/정류장과 완전 분리)
    //  - 데이터: tramRouteData.js에서 window.TRAM_ROUTE_FULL_HD 제공
    //  - 표기: name 미표기, id 숫자만 표기 (정수 id만)
    // =========================================================
    var tramLineSource = new ol.source.Vector(); // 트램 라인 소스
    var tramLineLayer = new ol.layer.Vector({
        // 트램 라인 레이어
        source: tramLineSource, // 소스 연결
        zIndex: 4, // z-index (버스/정류장/버스노선 라인보다 아래)
        // ✅ style은 feature별로 직접 설정(섹션별 색상 적용)하므로 레이어 고정 style 사용 안 함
    });

    var tramStopSource = new ol.source.Vector(); // 트램 정거장 소스
    var tramStopLayer = new ol.layer.Vector({
        // 트램 정거장 레이어
        source: tramStopSource, // 소스 연결
        zIndex: 8, // 정류장(stopLayer=10)보다는 아래/비슷, 필요시 조정 가능
    });

    function isIntegerId(idVal) {
        // 정수 ID인지 체크 (201 같은 것만 라벨)
        if (idVal == null) return false; // null/undefined 방지
        var n = Number(idVal); // 숫자 변환
        return Number.isFinite(n) && Math.floor(n) === n; // 정수 여부
    }

    function clearTram() {
        // 트램 라인/정거장 초기화
        tramLineSource.clear(); // 라인 제거
        tramStopSource.clear(); // 정거장 제거
    }

    // ✅ (섹션별 세그먼트) 트램 라인 생성 헬퍼
    function addTramSegmentFeature(coords5179, sectionName) {
        // 세그먼트 피처 추가
        if (!coords5179 || coords5179.length < 2) return; // 최소 2점 필요

        var color = getTramSectionColor(sectionName); // 섹션 색상
        var f = new ol.Feature({
            // 라인 피처 생성
            geometry: new ol.geom.LineString(coords5179),
        });

        f.set('featureType', 'tram_line'); // 타입 지정(충돌 방지)
        f.set('section', sectionName || ''); // 섹션 저장

        // ✅ 섹션별 색상 스타일 적용
        f.setStyle(getTramLineStyleByColor(color));

        tramLineSource.addFeature(f); // 소스에 추가
    }

    function drawTramLine(tramData) {
        // 트램 라인 그리기 (섹션별 색상)
        if (!olMap) return; // 지도 없으면 중단
        tramLineSource.clear(); // 기존 라인 제거
        if (!tramData || !tramData.length) return; // 데이터 없으면 중단

        // ✅ 연속된 점들을 section 기준으로 묶어서 세그먼트로 만든다.
        // ✅ (수정 핵심) 섹션이 바뀌는 순간에도 라인이 끊기지 않도록,
        //             "이전 세그먼트 마지막 점"을 "새 세그먼트 첫 점"으로 포함해서 이어준다.
        var currentSection = null; // 현재 세그먼트 섹션
        var currentCoords = []; // 현재 세그먼트 좌표

        tramData.forEach(function (p) {
            if (!p) return; // null 방지

            var lat = parseFloat(p.lat); // 위도
            var lng = parseFloat(p.lng); // 경도
            if (isNaN(lat) || isNaN(lng)) return; // 좌표 이상 스킵

            var sectionName = p.section || ''; // 구간 이름
            var xy5179 = ol.proj.transform([lng, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환

            // 세그먼트 시작
            if (currentSection === null) {
                currentSection = sectionName;
                currentCoords = [xy5179];
                return;
            }

            // 섹션이 바뀌면 이전 세그먼트 확정 후 새 세그먼트 시작
            if (sectionName !== currentSection) {
                // 이전 세그먼트 추가
                addTramSegmentFeature(currentCoords, currentSection);

                // ✅ 경계 연결: 이전 세그먼트 마지막 점을 다음 세그먼트 첫 점으로 포함
                var lastPointOfPrev = currentCoords && currentCoords.length > 0 ? currentCoords[currentCoords.length - 1] : null;

                currentSection = sectionName; // 섹션 갱신

                if (lastPointOfPrev) {
                    currentCoords = [lastPointOfPrev, xy5179]; // ✅ 끊김 방지 연결
                } else {
                    currentCoords = [xy5179]; // 방어 코드(이론상 거의 안 탐)
                }
                return;
            }

            // 같은 섹션이면 이어 붙이기
            currentCoords.push(xy5179);
        });

        // 마지막 세그먼트 확정
        addTramSegmentFeature(currentCoords, currentSection);
    }

    function drawTramStops(tramData) {
        // 트램 정거장 번호(정수 id)만 표시 (섹션색 외곽선)
        if (!olMap) return; // 지도 없으면 중단
        tramStopSource.clear(); // 기존 정거장 제거
        if (!tramData || !tramData.length) return; // 데이터 없으면 중단

        tramData.forEach(function (p) {
            if (!p) return; // 데이터 null 방지

            // waypoint는 라벨/정거장 표시 제외 (선 보정점이므로)
            if (p.type === 'waypoint') return;

            // 205.5 같은 값은 표시 제외, 201 같은 정수만 표시
            if (!isIntegerId(p.id)) return;

            var lat = parseFloat(p.lat); // 위도
            var lng = parseFloat(p.lng); // 경도
            if (isNaN(lat) || isNaN(lng)) return; // 좌표 이상하면 스킵

            var sectionColor = getTramSectionColor(p.section); // ✅ 정거장도 섹션색으로 테두리

            var xy5179 = ol.proj.transform([lng, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환
            var feature = new ol.Feature({
                // 피처 생성
                geometry: new ol.geom.Point(xy5179), // 포인트 생성
            });

            feature.set('featureType', 'tram_stop'); // 타입 지정(충돌 방지)

            feature.setStyle([
                // 점 + 텍스트(번호)
                new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 6, // 점 크기
                        fill: new ol.style.Fill({ color: '#ffffff' }), // 내부 흰색
                        stroke: new ol.style.Stroke({ color: sectionColor, width: 3 }), // ✅ 외곽 섹션색
                    }),
                    zIndex: 8,
                }),
                new ol.style.Style({
                    text: new ol.style.Text({
                        text: String(p.id), // ✅ 이름 대신 id만
                        font: 'bold 12px "Pretendard", sans-serif',
                        fill: new ol.style.Fill({ color: '#111' }),
                        stroke: new ol.style.Stroke({ color: '#fff', width: 4 }),
                        offsetY: -16,
                        textAlign: 'center',
                    }),
                    zIndex: 9,
                }),
            ]);

            tramStopSource.addFeature(feature); // 소스에 추가
        });
    }

    function drawTramOnMapIfExists() {
        // 데이터 있으면 트램 표시
        // tramRouteData.js가 window.TRAM_ROUTE_FULL_HD 를 제공한다는 전제
        var data = window.TRAM_ROUTE_FULL_HD || window.TRAM_STATIONS || null; // 우선순위: FULL_HD -> STATIONS
        if (!data || !data.length) {
            // 데이터 없으면
            clearTram(); // 트램 제거
            return; // 종료
        }
        drawTramLine(data); // ✅ 섹션별 라인 그림
        drawTramStops(data); // ✅ 섹션별 정거장 테두리 색 적용
    }

    // =========================================================
    // [트램] 토글 동작 함수 (실제 로직)
    // =========================================================
    $scope.toggleTramLayer = function () {
        // ✅ HTML에서 바로 호출됨
        $scope.isTramVisible = !$scope.isTramVisible; // 토글

        if ($scope.isTramVisible) {
            drawTramOnMapIfExists(); // 보이기
        } else {
            clearTram(); // 지우기
        }
    };

    // -------------------------
    // 벡터 레이어 준비 (정류장/버스)
    // -------------------------
    var stopSource = new ol.source.Vector(); // 정류장 소스 생성
    var stopLayer = new ol.layer.Vector({
        // 정류장 레이어 생성
        source: stopSource, // 소스 연결
        zIndex: 10, // z-index 설정 (버스 아래)
    });

    var busSource = new ol.source.Vector(); // 버스 소스 생성
    var busLayer = new ol.layer.Vector({
        // 버스 레이어 생성
        source: busSource, // 소스 연결
        zIndex: 20, // z-index 설정 (정류장 위)
    });

    // -------------------------
    // 노선 라인 레이어 (파란색)
    // -------------------------
    var routeLineSource = new ol.source.Vector(); // 노선 라인 소스 생성
    var routeLineLayer = new ol.layer.Vector({
        // 노선 라인 레이어 생성
        source: routeLineSource, // 소스 연결
        zIndex: 5, // z-index 설정 (가장 아래)
        style: new ol.style.Style({
            // 스타일 설정
            stroke: new ol.style.Stroke({
                // 선 스타일
                color: 'rgba(0, 102, 255, 0.7)', // 색상 (반투명 파랑)
                width: 5, // 두께
                lineCap: 'round', // 끝 모양 둥글게
                lineJoin: 'round', // 연결부 둥글게
            }),
        }),
    });

    // -------------------------
    // 노선 라인 화살표
    // -------------------------
    var ROUTE_ARROW_EVERY_N_SEGMENTS = 2; // 화살표 간격 (세그먼트 수)
    var ROUTE_ARROW_MIN_SEGMENT_LEN = 30; // 화살표 표시 최소 길이
    var ROUTE_ARROW_ROTATION_OFFSET = 0; // 화살표 기본 방향이 다를 때 보정(예: Math.PI/2, Math.PI 등)
    var routeArrowStyleCache = {}; // 화살표 스타일 캐시

    function buildRouteArrowSvgDataUri(fillColor) {
        // 화살표 SVG 생성 함수
        var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' + '<path fill="' + fillColor + '" d="M4 12h11.2l-3.6-3.6L13 7l7 7-7 7-1.4-1.4 3.6-3.6H4z"/>' + '</svg>';

        return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg); // Data URI 반환
    }

    function getRouteArrowStyle(rotationRad) {
        // 화살표 스타일 반환 함수
        var rot = rotationRad + ROUTE_ARROW_ROTATION_OFFSET; // 회전 오프셋 반영

        // ✅ 핵심: OpenLayers Icon.rotation은 +가 시계방향이라서 부호를 뒤집어준다
        rot = -rot;

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
    var hoverTooltipEl = null; // 툴팁 DOM 요소
    var hoverTooltipOverlay = null; // 툴팁 오버레이 객체

    function initHoverTooltip() {
        // 툴팁 초기화 함수
        if (!olMap) return; // 지도 없으면 중단
        if (hoverTooltipOverlay) return; // 이미 있으면 중단

        var mapDiv = document.getElementById('map1'); // 지도 컨테이너
        if (!mapDiv) return; // 컨테이너 없으면 중단

        hoverTooltipEl = document.createElement('div'); // div 생성
        hoverTooltipEl.style.position = 'absolute'; // 절대 위치
        hoverTooltipEl.style.pointerEvents = 'none'; // 마우스 이벤트 통과
        hoverTooltipEl.style.background = 'rgba(0, 0, 0, 0.8)'; // 배경색
        hoverTooltipEl.style.color = '#ffffff'; // 글자색
        hoverTooltipEl.style.padding = '8px 12px'; // 패딩
        hoverTooltipEl.style.borderRadius = '6px'; // 테두리 둥글게
        hoverTooltipEl.style.fontSize = '13px'; // 글자 크기
        hoverTooltipEl.style.whiteSpace = 'nowrap'; // 줄바꿈 금지
        hoverTooltipEl.style.display = 'none'; // 숨김 상태
        hoverTooltipEl.style.zIndex = '9999'; // z-index 설정
        hoverTooltipEl.style.boxShadow = '0 2px 5px rgba(0,0,0,0.2)'; // 그림자

        mapDiv.appendChild(hoverTooltipEl); // 지도에 추가

        hoverTooltipOverlay = new ol.Overlay({
            // 오버레이 생성
            element: hoverTooltipEl, // 요소 연결
            offset: [15, 0], // 위치 오프셋
            positioning: 'center-left', // 기준 위치
            stopEvent: false, // 이벤트 전파 허용
        });

        olMap.addOverlay(hoverTooltipOverlay); // 지도에 오버레이 추가

        mapDiv.addEventListener('mouseleave', function () {
            // 마우스 이탈 시
            hideHoverTooltip(); // 툴팁 숨김
        });

        olMap.on('pointermove', function (evt) {
            // 마우스 이동 시
            if (evt.dragging) {
                // 드래그 중이면
                hideHoverTooltip(); // 툴팁 숨김
                return;
            }

            var isRouteMode = !!$scope.currentRouteId; // 노선 모드 확인
            var isStopSearchMode = !isRouteMode && $scope.stops && $scope.stops.length > 0; // 정류장 모드 확인

            if (!isRouteMode && !isStopSearchMode) {
                // 둘 다 아니면
                hideHoverTooltip(); // 툴팁 숨김
                return;
            }

            var pixel = olMap.getEventPixel(evt.originalEvent); // 픽셀 좌표 획득

            var feature = olMap.forEachFeatureAtPixel(
                pixel,
                function (f) {
                    return f;
                },
                {
                    layerFilter: function (layer) {
                        // 레이어 필터
                        return layer !== repPulseLayer; // 펄스 레이어 제외
                    },
                }
            );

            if (!feature) {
                // 피처 없으면
                hideHoverTooltip(); // 툴팁 숨김
                return;
            }

            var fType = feature.get('featureType'); // 피처 타입 확인

            // 정류장 호버
            if (fType === 'stop') {
                var stopData = feature.get('stopData') || null; // 데이터 획득
                var stopName = (stopData && (stopData.nodenm || stopData.stationName)) || feature.get('name') || ''; // 이름 획득

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
        // 툴팁 표시 함수
        if (!hoverTooltipEl || !hoverTooltipOverlay) return;
        hoverTooltipEl.textContent = text;
        hoverTooltipEl.style.display = 'block';
        hoverTooltipOverlay.setPosition(coord);
    }

    function hideHoverTooltip() {
        // 툴팁 숨김 함수
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
                geometry: new ol.geom.Point(xy5179),
            });
            repPulseSource.addFeature(repPulseFeature);
        } else {
            repPulseFeature.setGeometry(new ol.geom.Point(xy5179));
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

        var center5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
        var view = olMap.getView();
        if (!view) return;

        var currentZoom = view.getZoom();
        var targetZoom = currentZoom;
        if (typeof currentZoom === 'number') {
            targetZoom = Math.min(REP_ZOOM_MAX, currentZoom + REP_ZOOM_IN_DELTA);
        }

        view.animate({ center: center5179, duration: 800 }, { zoom: targetZoom, duration: 800 });

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

            // ✅ RoutePicker(노선따기) ON이면 여기 singleclick 로직은 간섭하지 않게 즉시 종료
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

        if (olMap && typeof olMap.addLayer === 'function') {
            // ✅ 트램 레이어는 항상 추가(표시/숨김은 source clear로 제어)
            olMap.addLayer(tramLineLayer);
            olMap.addLayer(tramStopLayer);

            // 기존 레이어
            olMap.addLayer(routeLineLayer);
            olMap.addLayer(stopLayer);
            olMap.addLayer(busLayer);
            olMap.addLayer(repPulseLayer);

            console.log('레이어 추가 완료 (트램 포함)');
        }

        initHoverTooltip();
        initBusClickToShowRouteLine();

        // ✅ 초기에는 무조건 트램을 "지움" 상태로 둔다 (보이기 눌러야만 표시)
        clearTram();
        $scope.isTramVisible = false;
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
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
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
        if (isNaN(lat) || isNaN(lon)) return;

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
            var feature = new ol.Feature({
                geometry: new ol.geom.Point(xy5179),
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
            var lat = parseFloat(stop.gpslati || stop.gpsLati || stop.gpsY);
            var lon = parseFloat(stop.gpslong || stop.gpsLong || stop.gpsX);

            if (!isNaN(lat) && !isNaN(lon)) {
                var center = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179');
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

        // ✅ RoutePicker 켜진 채로 페이지 이동/컨트롤러 종료될 수 있으니 정리
        if ($scope.isRoutePickerOn) {
            $scope.disableRoutePicker();
        }
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

// 수집 상태(서버 /collector/status 응답을 그대로 담는 용도)
$scope.collectorStatus = { running: false, batchSize: 5, intervalMs: 5000, lastElapsedMs: 0, inProgress: false };
$scope.collectorStatusText = '상태: OFF';

// 상태 조회
function refreshCollectorStatus() {
    $http.get('/collector/status').then(
        function (res) {
            var d = res && res.data ? res.data : null;
            if (!d) return;

            $scope.collectorStatus = d;

            if (d.running) {
                $scope.collectorStatusText =
                    '상태: ON · batch ' + d.batchSize + ' · ' + d.lastElapsedMs + 'ms' + (d.inProgress ? ' · 실행중' : '');
            } else {
                $scope.collectorStatusText = '상태: OFF';
            }
        },
        function (err) {
            $scope.collectorStatusText = '상태 조회 실패: ' + ((err && err.status != null) ? err.status : 'UNKNOWN');
        }
    );
}

// 토글 버튼
$scope.toggleCollector = function () {
    $http.get('/collector/toggle').then(
        function (res) {
            // 토글 응답도 status 형태로 오므로 그대로 반영
            var d = res && res.data ? res.data : null;
            if (d) $scope.collectorStatus = d;

            // 토글 직후 텍스트 갱신
            refreshCollectorStatus();
        },
        function (err) {
            $scope.collectorStatusText = '토글 실패: ' + ((err && err.status != null) ? err.status : 'UNKNOWN');
        }
    );
};

// 수정됨: collector 상태 폴링을 ON 상태일 때만 수행하도록 구조 변경

var collectorPoll = null;

/**
 * 상태 폴링 시작 (중복 방지)
 */
function startCollectorPoll() {
    if (collectorPoll) return;
    collectorPoll = $interval(refreshCollectorStatus, 10000);
}

/**
 * 상태 폴링 중지
 */
function stopCollectorPoll() {
    if (!collectorPoll) return;
    $interval.cancel(collectorPoll);
    collectorPoll = null;
}

/**
 * 서버에서 받은 collector 상태를 UI 및 폴링 상태에 반영
 */
function applyCollectorStatus(d) {
    $scope.collectorStatus = d;

    if (d && d.running) {
        $scope.collectorStatusText =
            '상태: ON · batch ' + d.batchSize +
            (d.inProgress ? ' · 실행중' : '');
        startCollectorPoll();     // ✅ ON일 때만 폴링 시작
    } else {
        $scope.collectorStatusText = '상태: OFF';
        stopCollectorPoll();      // ✅ OFF면 폴링 중지
    }
}

/**
 * 상태 조회
 */
function refreshCollectorStatus() {
    $http.get('/collector/status').then(function (res) {
        applyCollectorStatus(res.data);
    });
}

/**
 * 페이지 진입 시: 상태는 1번만 확인
 * (ON이면 applyCollectorStatus에서 자동으로 폴링 시작됨)
 */
refreshCollectorStatus();

/**
 * 페이지 이탈 시 폴링 정리
 */
$scope.$on('$destroy', function () {
    stopCollectorPoll();
});

// 수정됨 끝


});

// 수정됨 끝
