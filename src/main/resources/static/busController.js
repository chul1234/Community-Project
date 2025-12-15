// 수정됨: 정류장 선택 시 '줌인+이동' 기능 추가 + 기존 디자인 유지

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

    $scope.searchType    = 'route'; // 검색 타입 (기본: 노선)
    $scope.searchKeyword = ''; // 검색어 입력값
    $scope.searchTerm    = ''; // 실제 검색어

    $scope.map1 = null; // NGII 지도 객체
    var olMap = null; // OpenLayers 지도 객체

    $scope.routeResultJson    = ''; // 노선 검색 결과 JSON
    $scope.stopsResultJson    = ''; // 정류장 검색 결과 JSON
    $scope.locationResultJson = ''; // 버스 위치 결과 JSON

    $scope.currentRouteId = null; // 현재 선택된 노선 ID

    $scope.stops = []; // 정류장 목록 배열
    $scope.selectedStop = null; // 선택된 정류장 객체

    var autoRefreshPromise = null; // 자동 새로고침 Promise
    $scope.isAutoRefreshOn = false; // 자동 새로고침 상태 플래그

    $scope.isMapLoading = false; // 지도 로딩 상태 플래그

    $scope.representativeBus = null; // 대표 버스 객체

    $scope.prevStop    = null; // 이전 정류장
    $scope.currentStop = null; // 현재 정류장
    $scope.nextStop    = null; // 다음 정류장

    $scope.arrivalList = []; // 도착 예정 버스 목록

    var lastArrivalDrawRequestId = 0; // 도착 정보 그리기 요청 ID (비동기 처리용)

    // 정류장 모드: 버스 클릭 시 임시 노선 ID
    $scope.tempRouteIdFromStop = null;

    // =========================================================
    // [디자인] SVG 아이콘 생성 함수
    // =========================================================
    function createSvgIcon(color, type) {
        var svg = ''; // SVG 문자열 초기화
        // 버스 아이콘일 경우
        if (type === 'bus') {
            svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">' + // SVG 헤더
                  '<path fill="' + color + '" d="M48 64C48 28.7 76.7 0 112 0H400c35.3 0 64 28.7 64 64V448c0 35.3-28.7 64-64 64H384c-17.7 0-32-14.3-32-32s14.3-32 32-32h16c8.8 0 16-7.2 16-16V384H96v64c0 8.8 7.2 16 16 16h16c17.7 0 32 14.3 32 32s-14.3 32-32 32H112c-35.3 0-64-28.7-64-64V64zm32 32c0-17.7 14.3-32 32-32H400c17.7 0 32 14.3 32 32v64c0 17.7-14.3 32-32 32H112c-17.7 0-32-14.3-32-32V96zm0 160c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H80zm352 0c-17.7 0-32 14.3-32 32v32c0 17.7 14.3 32 32 32h32c17.7 0 32-14.3 32-32V288c0-17.7-14.3-32-32-32H432z"/>' + // 버스 경로 데이터
                  '</svg>'; // SVG 종료 태그
        }
        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg); // Data URI 반환
    }

    // -------------------------
    // 벡터 레이어 준비 (정류장/버스)
    // -------------------------
    var stopSource = new ol.source.Vector(); // 정류장 소스 생성
    var stopLayer  = new ol.layer.Vector({ // 정류장 레이어 생성
        source: stopSource, // 소스 연결
        zIndex: 10 // z-index 설정 (버스 아래)
    });

    var busSource = new ol.source.Vector(); // 버스 소스 생성
    var busLayer  = new ol.layer.Vector({ // 버스 레이어 생성
        source: busSource, // 소스 연결
        zIndex: 20 // z-index 설정 (정류장 위)
    });

    // -------------------------
    // 노선 라인 레이어 (파란색)
    // -------------------------
    var routeLineSource = new ol.source.Vector(); // 노선 라인 소스 생성
    var routeLineLayer  = new ol.layer.Vector({ // 노선 라인 레이어 생성
        source: routeLineSource, // 소스 연결
        zIndex: 5, // z-index 설정 (가장 아래)
        style: new ol.style.Style({ // 스타일 설정
            stroke: new ol.style.Stroke({ // 선 스타일
                color: 'rgba(0, 102, 255, 0.7)', // 색상 (반투명 파랑)
                width: 5, // 두께
                lineCap: 'round', // 끝 모양 둥글게
                lineJoin: 'round' // 연결부 둥글게
            })
        })
    });

    // -------------------------
    // 노선 라인 화살표
    // -------------------------
    var ROUTE_ARROW_EVERY_N_SEGMENTS = 2; // 화살표 간격 (세그먼트 수)
    var ROUTE_ARROW_MIN_SEGMENT_LEN  = 30; // 화살표 표시 최소 길이
    var routeArrowStyleCache         = {}; // 화살표 스타일 캐시

    function buildRouteArrowSvgDataUri(fillColor) { // 화살표 SVG 생성 함수
        var svg = // SVG 문자열
            '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' +
            '<path fill="' + fillColor + '" d="M4 12h11.2l-3.6-3.6L13 7l7 7-7 7-1.4-1.4 3.6-3.6H4z"/>' +
            '</svg>';

        return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg); // Data URI 반환
    }

    function getRouteArrowStyle(rotationRad) { // 화살표 스타일 반환 함수
        var key = (Math.round(rotationRad * 100) / 100).toString(); // 회전각 키 생성
        if (routeArrowStyleCache[key]) return routeArrowStyleCache[key]; // 캐시 확인

        routeArrowStyleCache[key] = new ol.style.Style({ // 스타일 생성
            image: new ol.style.Icon({ // 아이콘 설정
                src: buildRouteArrowSvgDataUri('#0066ff'), // 이미지 소스
                rotateWithView: true, // 지도 회전 연동
                rotation: rotationRad, // 회전각 설정
                scale: 0.7, // 크기 조절
                opacity: 0.95 // 투명도 설정
            })
        });

        return routeArrowStyleCache[key]; // 스타일 반환
    }

    // -------------------------
    // 툴팁 (Hover)
    // -------------------------
    var hoverTooltipEl = null; // 툴팁 DOM 요소
    var hoverTooltipOverlay = null; // 툴팁 오버레이 객체

    function initHoverTooltip() { // 툴팁 초기화 함수
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

        hoverTooltipOverlay = new ol.Overlay({ // 오버레이 생성
            element: hoverTooltipEl, // 요소 연결
            offset: [15, 0], // 위치 오프셋
            positioning: 'center-left', // 기준 위치
            stopEvent: false // 이벤트 전파 허용
        });

        olMap.addOverlay(hoverTooltipOverlay); // 지도에 오버레이 추가

        mapDiv.addEventListener('mouseleave', function () { // 마우스 이탈 시
            hideHoverTooltip(); // 툴팁 숨김
        });

        olMap.on('pointermove', function (evt) { // 마우스 이동 시
            if (evt.dragging) { // 드래그 중이면
                hideHoverTooltip(); // 툴팁 숨김
                return;
            }

            var isRouteMode = !!$scope.currentRouteId; // 노선 모드 확인
            var isStopSearchMode = !isRouteMode && ($scope.stops && $scope.stops.length > 0); // 정류장 모드 확인

            if (!isRouteMode && !isStopSearchMode) { // 둘 다 아니면
                hideHoverTooltip(); // 툴팁 숨김
                return;
            }

            var pixel = olMap.getEventPixel(evt.originalEvent); // 픽셀 좌표 획득

            var feature = olMap.forEachFeatureAtPixel( // 피처 감지
                pixel,
                function (f) { return f; },
                {
                    layerFilter: function (layer) { // 레이어 필터
                        return layer !== repPulseLayer; // 펄스 레이어 제외
                    }
                }
            );

            if (!feature) { // 피처 없으면
                hideHoverTooltip(); // 툴팁 숨김
                return;
            }

            var fType = feature.get('featureType'); // 피처 타입 확인

            // 정류장 호버
            if (fType === 'stop') {
                var stopData = feature.get('stopData') || null; // 데이터 획득
                var stopName = (stopData && (stopData.nodenm || stopData.stationName)) || feature.get('name') || ''; // 이름 획득

                if (!stopName) { // 이름 없으면
                    hideHoverTooltip(); // 숨김
                    return;
                }
                showHoverTooltip(evt.coordinate, '🚏 ' + stopName); // 툴팁 표시
                return;
            }

            // 버스 호버
            if (fType === 'bus') {
                var busData = feature.get('busData') || null; // 데이터 획득
                if (!busData) { // 데이터 없으면
                    hideHoverTooltip(); // 숨김
                    return;
                }

                var routeNo = (busData.routenm != null ? String(busData.routenm) : '') || (busData.routeno != null ? String(busData.routeno) : '') || ''; // 노선번호
                var vehicleNo = (busData.vehicleno != null ? String(busData.vehicleno) : '') || ''; // 차량번호

                var parts = []; // 텍스트 조합 배열
                if (routeNo) parts.push(routeNo + '번'); // 노선번호 추가
                if (vehicleNo) parts.push(vehicleNo); // 차량번호 추가

                if (isRouteMode) { // 노선 모드면
                    var calc = computePrevCurrentNextForBus(busData, $scope.stops || []); // 이전/다음 계산
                    var nextStopName = (calc && calc.next && (calc.next.nodenm || calc.next.stationName)) || ''; // 다음 정류장
                    if (nextStopName) parts.push('→ ' + nextStopName); // 다음 정류장 추가
                }

                var text = parts.join(' | '); // 텍스트 결합
                if (!text) { // 텍스트 없으면
                    hideHoverTooltip(); // 숨김
                    return;
                }
                showHoverTooltip(evt.coordinate, '🚌 ' + text); // 툴팁 표시
                return;
            }

            hideHoverTooltip(); // 그 외 숨김
        });
    }

    function showHoverTooltip(coord, text) { // 툴팁 표시 함수
        if (!hoverTooltipEl || !hoverTooltipOverlay) return; // 요소 없으면 중단
        hoverTooltipEl.textContent = text; // 텍스트 설정
        hoverTooltipEl.style.display = 'block'; // 보이기
        hoverTooltipOverlay.setPosition(coord); // 위치 설정
    }

    function hideHoverTooltip() { // 툴팁 숨김 함수
        if (!hoverTooltipEl || !hoverTooltipOverlay) return; // 요소 없으면 중단
        hoverTooltipEl.style.display = 'none'; // 숨기기
        hoverTooltipOverlay.setPosition(undefined); // 위치 해제
    }

    // -------------------------
    // 대표 버스 펄스(파동) 애니메이션
    // -------------------------
    var repPulseSource = new ol.source.Vector(); // 펄스 소스 생성
    var repPulseLayer  = new ol.layer.Vector({ // 펄스 레이어 생성
        source: repPulseSource, // 소스 연결
        zIndex: 15, // z-index 설정
        style: function () { // 스타일 함수
            if (!$scope.representativeBus) return null; // 대표 버스 없으면 null
            if (!$scope.currentRouteId) return null; // 노선 ID 없으면 null

            var t = Date.now(); // 현재 시간
            var phase = (t % 1500) / 1500.0; // 애니메이션 단계 (0~1)
            var radius = 5 + (phase * 20); // 반지름 계산
            var opacity = 1.0 - phase; // 투명도 계산

            var pulseColor = '255, 149, 0'; // 펄스 색상 (주황)

            return new ol.style.Style({ // 스타일 생성
                image: new ol.style.Circle({ // 원형 이미지
                    radius: radius, // 반지름
                    stroke: new ol.style.Stroke({ // 테두리
                        color: 'rgba(' + pulseColor + ', ' + opacity.toFixed(3) + ')', // 색상
                        width: 2 + (2 * (1 - phase)) // 두께
                    }),
                    fill: new ol.style.Fill({ // 채우기
                        color: 'rgba(' + pulseColor + ', ' + (opacity * 0.1).toFixed(3) + ')' // 색상
                    })
                })
            });
        }
    });

    var repPulseFeature = null; // 펄스 피처 변수
    var repPulseRafId = null; // 애니메이션 프레임 ID

    function startRepPulseAnimationLoop() { // 애니메이션 시작 함수
        if (!olMap) return; // 지도 없으면 중단
        if (repPulseRafId != null) return; // 이미 실행 중이면 중단

        var tick = function () { // 프레임 함수
            if (!olMap || !$scope.representativeBus || !$scope.currentRouteId) { // 조건 불만족 시
                repPulseRafId = null; // ID 초기화
                return; // 종료
            }
            olMap.render(); // 지도 렌더링
            repPulseRafId = requestAnimationFrame(tick); // 다음 프레임 요청
        };
        repPulseRafId = requestAnimationFrame(tick); // 첫 프레임 요청
    }

    function stopRepPulseAnimationLoop() { // 애니메이션 중지 함수
        if (repPulseRafId != null) { // 실행 중이면
            cancelAnimationFrame(repPulseRafId); // 취소
            repPulseRafId = null; // ID 초기화
        }
    }

    function clearRepPulse() { // 펄스 초기화 함수
        repPulseSource.clear(); // 소스 비우기
        repPulseFeature = null; // 피처 초기화
        stopRepPulseAnimationLoop(); // 애니메이션 중지
    }

    function updateRepPulseFeatureByBus(bus) { // 펄스 피처 업데이트 함수
        if (!olMap) return; // 지도 없으면 중단
        if (!bus) { // 버스 없으면
            clearRepPulse(); // 초기화
            return;
        }

        var lat = parseFloat(bus.gpslati); // 위도
        var lon = parseFloat(bus.gpslong); // 경도
        if (isNaN(lat) || isNaN(lon)) { // 좌표 유효성 검사
            clearRepPulse(); // 초기화
            return;
        }

        var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환

        if (!repPulseFeature) { // 피처 없으면
            repPulseFeature = new ol.Feature({ // 생성
                geometry: new ol.geom.Point(xy5179) // 지오메트리 설정
            });
            repPulseSource.addFeature(repPulseFeature); // 소스에 추가
        } else { // 있으면
            repPulseFeature.setGeometry(new ol.geom.Point(xy5179)); // 위치 업데이트
        }
        startRepPulseAnimationLoop(); // 애니메이션 시작
    }

    // -------------------------
    // 대표 버스 지도 이동
    // -------------------------
    var lastRepVehicleNoForPan = null; // 마지막 이동 차량번호
    var lastRepPanAtMs = 0; // 마지막 이동 시간
    var REP_ZOOM_IN_DELTA = 1; // 줌 증가량
    var REP_ZOOM_MAX      = 15; // 최대 줌 레벨

    function panToRepresentativeBusIfNeeded(bus) { // 지도 이동 함수
        if (!olMap) return; // 지도 없으면 중단
        if (!bus) return; // 버스 없으면 중단
        if (!$scope.currentRouteId) return; // 노선 ID 없으면 중단

        var vehicleno = (bus.vehicleno != null) ? String(bus.vehicleno) : null; // 차량번호
        if (!vehicleno) return; // 차량번호 없으면 중단

        if (lastRepVehicleNoForPan === vehicleno) return; // 같은 차량이면 중단

        var now = Date.now(); // 현재 시간
        if (now - lastRepPanAtMs < 1000) return; // 1초 내 재이동 방지

        var lat = parseFloat(bus.gpslati); // 위도
        var lon = parseFloat(bus.gpslong); // 경도
        if (isNaN(lat) || isNaN(lon)) return; // 좌표 유효성 검사

        var center5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환
        var view = olMap.getView(); // 뷰 객체 획득
        if (!view) return; // 뷰 없으면 중단

        var currentZoom = view.getZoom(); // 현재 줌
        var targetZoom = currentZoom; // 타겟 줌
        if (typeof currentZoom === 'number') { // 줌 유효하면
            targetZoom = Math.min(REP_ZOOM_MAX, currentZoom + REP_ZOOM_IN_DELTA); // 줌 계산
        }

        view.animate( // 애니메이션 이동
            { center: center5179, duration: 800 }, // 중심 이동
            { zoom: targetZoom, duration: 800 } // 줌 이동
        );

        lastRepVehicleNoForPan = vehicleno; // 차량번호 갱신
        lastRepPanAtMs = now; // 시간 갱신
    }

    // -------------------------
    // JSON 파싱 함수
    // -------------------------
    function parseMaybeJson(data) { // 파싱 함수
        if (angular.isObject(data)) return data; // 객체면 반환
        if (!data) return null; // 데이터 없으면 null
        try {
            return JSON.parse(data); // 파싱 시도
        } catch (e) {
            console.error('JSON 파싱 실패:', e, data); // 에러 로그
            return null; // 실패 시 null
        }
    }

    // -------------------------
    // 정류장 모드: 버스 클릭 이벤트
    // -------------------------
    function initBusClickToShowRouteLine() { // 클릭 이벤트 초기화
        if (!olMap) return; // 지도 없으면 중단
        if (olMap.__busClickToRouteLineBound) return; // 이미 바인딩됐으면 중단

        olMap.__busClickToRouteLineBound = true; // 바인딩 플래그 설정

        olMap.on('singleclick', function (evt) { // 클릭 리스너 등록
            if (!olMap) return; // 지도 없으면 중단

            // 정류장 검색 모드 확인
            var isRouteMode = !!$scope.currentRouteId;
            var isStopSearchMode = !isRouteMode && ($scope.stops && $scope.stops.length > 0);
            if (!isStopSearchMode) return; // 아니면 중단

            var pixel = olMap.getEventPixel(evt.originalEvent); // 픽셀 좌표

            var feature = olMap.forEachFeatureAtPixel( // 피처 감지
                pixel,
                function (f) { return f; },
                {
                    layerFilter: function (layer) { // 레이어 필터
                        return layer !== repPulseLayer;
                    }
                }
            );

            if (!feature) return; // 피처 없으면 중단
            if (feature.get('featureType') !== 'bus') return; // 버스 아니면 중단

            var busData = feature.get('busData') || null; // 버스 데이터
            if (!busData) return; // 데이터 없으면 중단

            var routeId = busData.routeid || busData.routeId || busData.route_id || null; // 노선 ID
            if (!routeId) {
                console.warn('버스 클릭 감지했지만 routeId 없음:', busData); // 경고 로그
                return;
            }

            // 임시 노선 ID 설정
            $scope.tempRouteIdFromStop = String(routeId);

            clearRouteLine(); // 라인 초기화

            $http.get('/api/bus/route-stops', { // 정류장 목록 조회
                params: { routeId: routeId }
            }).then(function (res) { // 성공 시
                var data = parseMaybeJson(res.data); // 데이터 파싱
                if (!data || !data.response || !data.response.body) return; // 유효성 검사

                var items = data.response.body.items && data.response.body.items.item; // 아이템 추출
                if (!items) return; // 아이템 없으면 중단

                var stopsArray = angular.isArray(items) ? items : [items]; // 배열 변환
                drawRouteLineFromStops(stopsArray); // 라인 그리기

                if (!$scope.$$phase) $scope.$applyAsync(); // 스코프 적용
            }).catch(function (err) { // 에러 시
                console.error('버스 클릭 → 노선 정류장 조회 실패:', err); // 에러 로그
            });
        });
    }

    // -------------------------
    // 지도 초기화 함수
    // -------------------------
    $scope.initMap = function () { // 초기화 함수
        var mapDiv = document.getElementById('map1'); // 지도 컨테이너

        if (!window.ngii_wmts || !mapDiv) { // 필수 요소 확인
            console.error('NGII 지도 스크립트 미로드'); // 에러 로그
            return;
        }

        $scope.map1 = new ngii_wmts.map('map1', { // 지도 생성
            zoom: 3
        });

        if (typeof $scope.map1._getMap === 'function') { // _getMap 확인
            olMap = $scope.map1._getMap(); // olMap 획득
        } else {
            console.warn('_getMap 함수 없음'); // 경고 로그
            olMap = null;
        }

        if (olMap && typeof olMap.addLayer === 'function') { // 레이어 추가 확인
            olMap.addLayer(routeLineLayer); // 노선 레이어 추가
            olMap.addLayer(stopLayer); // 정류장 레이어 추가
            olMap.addLayer(busLayer); // 버스 레이어 추가
            olMap.addLayer(repPulseLayer); // 펄스 레이어 추가
            console.log('레이어 추가 완료 (디자인 적용됨)'); // 성공 로그
        }

        initHoverTooltip(); // 툴팁 초기화
        initBusClickToShowRouteLine(); // 클릭 이벤트 초기화
    };

    $timeout($scope.initMap, 0); // 타임아웃으로 실행

    // -------------------------
    // 노선 라인 관련 함수
    // -------------------------
    function clearRouteLine() { // 라인 지우기
        routeLineSource.clear(); // 소스 클리어
    }

    function drawRouteLineFromStops(stops) { // 라인 그리기 함수
        var routeIdForLine = $scope.currentRouteId || $scope.tempRouteIdFromStop; // 노선 ID 확인
        if (!routeIdForLine) { // 없으면
            clearRouteLine(); // 지우기
            return;
        }

        clearRouteLine(); // 초기화

        if (!olMap) return; // 지도 없으면 중단
        if (!stops || stops.length < 2) return; // 정류장 부족하면 중단

        var sortedStops = stops.slice().sort(function (a, b) { // 정류장 정렬
            var sa = parseInt(a.routeseq || a.routeSeq || 0, 10); // 순번 A
            var sb = parseInt(b.routeseq || b.routeSeq || 0, 10); // 순번 B
            return sa - sb; // 오름차순
        });

        var coordinates = []; // 좌표 배열
        sortedStops.forEach(function (s) { // 순회
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY); // 위도
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX); // 경도
            if (!isNaN(lat) && !isNaN(lon)) { // 유효성 검사
                var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환
                coordinates.push(xy5179); // 배열 추가
            }
        });

        if (coordinates.length < 2) return; // 좌표 부족하면 중단

        var lineFeature = new ol.Feature({ // 라인 피처 생성
            geometry: new ol.geom.LineString(coordinates) // 지오메트리 설정
        });
        routeLineSource.addFeature(lineFeature); // 소스에 추가

        // 화살표 그리기 루프
        for (var i = 0; i < coordinates.length - 1; i++) {
            if (ROUTE_ARROW_EVERY_N_SEGMENTS > 1 && (i % ROUTE_ARROW_EVERY_N_SEGMENTS) !== 0) continue; // 간격 체크

            var p1 = coordinates[i]; // 시작점
            var p2 = coordinates[i + 1]; // 끝점
            if (!p1 || !p2) continue; // 점 없으면 패스

            var dx = p2[0] - p1[0]; // X 차이
            var dy = p2[1] - p1[1]; // Y 차이
            var segLen = Math.sqrt(dx * dx + dy * dy); // 길이 계산

            if (!isFinite(segLen) || segLen < ROUTE_ARROW_MIN_SEGMENT_LEN) continue; // 길이 체크

            var mid = [(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2]; // 중간점
            var angle = Math.atan2(dy, dx); // 각도 계산

            var arrowFeature = new ol.Feature({ // 화살표 피처 생성
                geometry: new ol.geom.Point(mid) // 위치 설정
            });
            arrowFeature.setStyle(getRouteArrowStyle(angle)); // 스타일 설정
            routeLineSource.addFeature(arrowFeature); // 소스에 추가
        }

        // 라인 범위로 지도 줌
        var extent = routeLineSource.getExtent(); // 범위 획득
        if (extent && isFinite(extent[0])) { // 유효하면
            var view = olMap.getView(); // 뷰 획득
            if (view) {
                view.fit(extent, { // 줌 이동
                    padding: [60, 60, 60, 60], // 패딩
                    maxZoom: 14, // 최대 줌
                    duration: 500 // 지속 시간
                });
            }
        }
    }

    // -------------------------
    // 정류장 마커 관련 함수
    // -------------------------
    function clearStopMarkers() { // 정류장 지우기
        var newSrc = new ol.source.Vector(); // 새 소스 생성
        stopLayer.setSource(newSrc); // 레이어 소스 교체
        stopSource = newSrc; // 변수 갱신
    }

    function addStopMarkerToSource(targetSource, lat, lon, title, stopData, isSelected) { // 마커 추가 함수
        if (!olMap) return; // 지도 없으면 중단
        if (isNaN(lat) || isNaN(lon)) return; // 좌표 유효성 검사

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환
            var feature = new ol.Feature({ // 피처 생성
                geometry: new ol.geom.Point(xy5179), // 지오메트리
                name: title || '' // 이름
            });

            feature.set('featureType', 'stop'); // 타입 설정
            feature.set('stopData', stopData || null); // 데이터 설정

            // 스타일 변수 설정
            var fillColor   = isSelected ? '#007bff' : '#ffffff'; // 채우기 색
            var strokeColor = isSelected ? '#ffffff' : '#555555'; // 테두리 색
            var strokeWidth = isSelected ? 3 : 2; // 테두리 두께
            var radiusVal   = isSelected ? 8 : 5; // 반지름
            var zIndexVal   = isSelected ? 999 : 10; // z-index

            feature.setStyle( // 스타일 적용
                new ol.style.Style({
                    image: new ol.style.Circle({ // 원형
                        radius: radiusVal, // 반지름
                        fill: new ol.style.Fill({ color: fillColor }), // 채우기
                        stroke: new ol.style.Stroke({ color: strokeColor, width: strokeWidth }) // 테두리
                    }),
                    zIndex: zIndexVal // z-index
                })
            );

            targetSource.addFeature(feature); // 소스에 추가
        } catch (e) {
            console.warn('정류장 마커 오류:', e); // 에러 로그
        }
    }

    function fitMapToStops() { // 정류장 전체 보기
        if (!olMap) return; // 지도 없으면 중단
        var extent = stopSource.getExtent(); // 범위 획득
        if (!extent || !isFinite(extent[0])) return; // 유효성 검사

        var view = olMap.getView(); // 뷰 획득
        if (view) {
            view.fit(extent, { // 줌 이동
                padding: [50, 50, 50, 50], // 패딩
                maxZoom: 14, // 최대 줌
                duration: 500 // 지속 시간
            });
        }
    }

    function drawStopsOnMap(stops) { // 정류장 그리기 함수
        if (!stops || !stops.length) { // 정류장 없으면
            clearStopMarkers(); // 지우기
            return;
        }
        var newSrc = new ol.source.Vector(); // 새 소스
        stops.forEach(function (s) { // 순회
            var lat = parseFloat(s.gpslati || s.gpsLati || s.gpsY); // 위도
            var lon = parseFloat(s.gpslong || s.gpsLong || s.gpsX); // 경도
            
            // 선택된 정류장 확인
            var isSelected = ($scope.selectedStop && s === $scope.selectedStop);

            if (!isNaN(lat) && !isNaN(lon)) { // 유효성 검사
                addStopMarkerToSource(newSrc, lat, lon, s.nodenm || s.stationName || '', s, isSelected); // 마커 추가
            }
        });
        stopLayer.setSource(newSrc); // 레이어 소스 교체
        stopSource = newSrc; // 변수 갱신

        if (!$scope.selectedStop) { // 선택된 정류장 없으면
            fitMapToStops(); // 전체 보기
        }
    }

    // -------------------------
    // 버스 마커 관련 함수
    // -------------------------
    function clearBusMarkers() { // 버스 지우기
        var newSrc = new ol.source.Vector(); // 새 소스
        busLayer.setSource(newSrc); // 소스 교체
        busSource = newSrc; // 변수 갱신
    }

    function addBusMarkerToSource(targetSource, lat, lon, title, isRepresentative, busData) { // 버스 마커 추가
        if (!olMap) return; // 지도 없으면 중단
        if (isNaN(lat) || isNaN(lon)) return; // 좌표 유효성 검사

        try {
            var xy5179 = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환
            var feature = new ol.Feature({ // 피처 생성
                geometry: new ol.geom.Point(xy5179), // 지오메트리
                name: title || '' // 이름
            });

            feature.set('featureType', 'bus'); // 타입 설정
            feature.set('busData', busData || null); // 데이터 설정

            var busColor = isRepresentative ? '#ff9500' : '#007bff'; // 버스 색상
            var iconScale = isRepresentative ? 0.05 : 0.03; // 아이콘 크기
            var zIndexVal = isRepresentative ? 100 : 50; // z-index

            var busNoText = ''; // 버스 번호 텍스트
            if (!$scope.currentRouteId && title != null) { // 노선 모드 아니면
                busNoText = String(title).trim(); // 번호 설정
            }

            var styleArray = [ // 스타일 배열
                new ol.style.Style({ // 아이콘 스타일
                    image: new ol.style.Icon({
                        src: createSvgIcon(busColor, 'bus'), // SVG 아이콘
                        anchor: [0.5, 0.5], // 중심점
                        scale: iconScale, // 크기
                        opacity: 1.0, // 투명도
                        rotation: 0 // 회전
                    }),
                    zIndex: zIndexVal // z-index
                })
            ];

            if (busNoText) { // 텍스트 있으면
                styleArray.push(new ol.style.Style({ // 텍스트 스타일 추가
                    text: new ol.style.Text({
                        text: busNoText, // 텍스트
                        font: 'bold 12px "Pretendard", sans-serif', // 폰트
                        fill: new ol.style.Fill({ color: '#333' }), // 글자색
                        stroke: new ol.style.Stroke({ color: '#fff', width: 3 }), // 외곽선
                        offsetY: -15, // 위치 조정
                        textAlign: 'center' // 정렬
                    }),
                    zIndex: zIndexVal + 1 // z-index
                }));
            }

            feature.setStyle(styleArray); // 스타일 적용
            targetSource.addFeature(feature); // 소스에 추가

        } catch (e) {
            console.warn('버스 마커 오류:', e); // 에러 로그
        }
    }

    function drawBusLocationsOnMap(busItems) { // 버스 위치 그리기
        if (!busItems || !busItems.length) { // 데이터 없으면
            clearBusMarkers(); // 지우기
            return;
        }

        var newSrc = new ol.source.Vector(); // 새 소스
        var rep = $scope.representativeBus; // 대표 버스

        busItems.forEach(function (b) { // 순회
            var lat = parseFloat(b.gpslati); // 위도
            var lon = parseFloat(b.gpslong); // 경도
            if (!isNaN(lat) && !isNaN(lon)) { // 유효성 검사
                var label = (b.vehicleno || '') + ' / ' + (b.routenm || ''); // 라벨
                var isRepresentative = false; // 대표 여부
                if (rep && rep.vehicleno && b.vehicleno) { // 대표 버스 확인
                    isRepresentative = (rep.vehicleno === b.vehicleno);
                }
                addBusMarkerToSource(newSrc, lat, lon, String(label).trim(), isRepresentative, b); // 마커 추가
            }
        });

        busLayer.setSource(newSrc); // 소스 교체
        busSource = newSrc; // 변수 갱신
    }

    // -------------------------
    // API 호출 및 데이터 처리
    // -------------------------
    function computePrevCurrentNextForBus(bus, stops) { // 이전/현재/다음 정류장 계산
        var result = { prev: null, current: null, next: null }; // 결과 초기화
        if (!bus || !stops || !stops.length) return result; // 데이터 검사

        var currentIndex = -1; // 인덱스 초기화
        var busNodeId    = bus.nodeid || bus.nodeId || null; // 노드 ID
        var busSeq       = bus.routeseq || bus.routeSeq || null; // 순번

        if (busNodeId) { // 노드 ID로 검색
            for (var i = 0; i < stops.length; i++) {
                var s = stops[i];
                if ((s.nodeid || s.nodeId) === busNodeId) { currentIndex = i; break; } // 일치 시 중단
            }
        }

        if (currentIndex === -1 && busSeq != null) { // 순번으로 검색
            var busSeqNum = parseInt(busSeq, 10);
            if (!isNaN(busSeqNum)) {
                for (var j = 0; j < stops.length; j++) {
                    var st = stops[j];
                    var stopSeq = parseInt(st.routeseq || st.routeSeq, 10);
                    if (!isNaN(stopSeq) && stopSeq === busSeqNum) { currentIndex = j; break; } // 일치 시 중단
                }
            }
        }

        if (currentIndex === -1) return result; // 못 찾으면 반환
        result.current = stops[currentIndex]; // 현재 설정
        if (currentIndex > 0) result.prev = stops[currentIndex - 1]; // 이전 설정
        if (currentIndex < stops.length - 1) result.next = stops[currentIndex + 1]; // 다음 설정
        return result; // 결과 반환
    }

    function drawBusesForArrivalRoutes(arrivals) { // 도착 버스 그리기
        if ($scope.currentRouteId) return; // 노선 모드면 중단
        $scope.representativeBus = null; // 대표 버스 초기화
        clearRepPulse(); // 펄스 초기화
        lastRepVehicleNoForPan = null; // 이동 변수 초기화

        if (!arrivals || !arrivals.length) { // 데이터 없으면
            clearBusMarkers(); // 지우기
            return;
        }

        var routeIdMap = {}; // 노선 ID 맵
        arrivals.forEach(function (a) { // 순회
            var rid = a.routeid || a.routeId || a.route_id; // ID 추출
            if (rid) routeIdMap[rid] = true; // 맵에 추가
        });

        var routeIds = Object.keys(routeIdMap); // ID 목록
        if (!routeIds.length) { // 없으면
            clearBusMarkers(); // 지우기
            return;
        }

        lastArrivalDrawRequestId++; // 요청 ID 증가
        var myReqId = lastArrivalDrawRequestId; // 내 요청 ID
        var pending = routeIds.length; // 대기 카운트
        var tempSource = new ol.source.Vector(); // 임시 소스

        routeIds.forEach(function (rid) { // ID 순회
            $http.get('/api/bus/locations', { // API 호출
                params: { routeId: rid, pageNo: 1, numOfRows: 100 }
            }).then(function (res) { // 성공 시
                if (myReqId !== lastArrivalDrawRequestId) return; // 요청 ID 불일치 시 중단
                var data = parseMaybeJson(res.data); // 데이터 파싱
                if (!data || !data.response || !data.response.body) return; // 유효성 검사
                var items = data.response.body.items && data.response.body.items.item; // 아이템 추출
                if (!items) return; // 아이템 없으면 중단

                var busArray = angular.isArray(items) ? items : [items]; // 배열 변환
                busArray.forEach(function (b) { // 순회
                    if (!b.routeid && !b.routeId && !b.route_id) b.routeid = rid; // ID 설정
                    var lat = parseFloat(b.gpslati); // 위도
                    var lon = parseFloat(b.gpslong); // 경도
                    if (isNaN(lat) || isNaN(lon)) return; // 유효성 검사
                    var label = (b.routenm != null) ? String(b.routenm) : ''; // 라벨
                    addBusMarkerToSource(tempSource, lat, lon, String(label).trim(), false, b); // 마커 추가
                });
            }).catch(function (err) { // 에러 시
                console.error('정류장 모드 버스 위치 조회 실패:', err); // 에러 로그
            }).finally(function () { // 완료 시
                if (myReqId !== lastArrivalDrawRequestId) return; // 요청 ID 불일치 시 중단
                pending--; // 카운트 감소
                if (pending === 0) { // 모두 완료 시
                    busLayer.setSource(tempSource); // 소스 교체
                    busSource = tempSource; // 변수 갱신
                }
            });
        });
    }

    function fetchArrivalsForCurrentStop() { // 도착 정보 조회 함수
        if (!$scope.currentStop) return; // 정류장 없으면 중단
        var nodeId = $scope.currentStop.nodeid || $scope.currentStop.nodeId; // 노드 ID
        if (!nodeId) return; // ID 없으면 중단

        var previousArrivalList = $scope.arrivalList || []; // 이전 목록

        $http.get('/api/bus/arrivals', { // API 호출
            params: { nodeId: nodeId, numOfRows: 20 }
        }).then(function (res) { // 성공 시
            var data = parseMaybeJson(res.data); // 파싱
            if (!data || !data.response || !data.response.body) { // 유효성 검사
                $scope.arrivalList = previousArrivalList; // 이전 값 복원
                return;
            }
            var items = data.response.body.items && data.response.body.items.item; // 아이템 추출
            if (!items) { // 아이템 없으면
                $scope.arrivalList = []; // 목록 초기화
                clearBusMarkers(); // 마커 초기화
                return;
            }
            var list = angular.isArray(items) ? items : [items]; // 배열 변환
            var mapped = list.map(function (a) { // 매핑
                var remainStops = (a.arrprevstationcnt != null) ? parseInt(a.arrprevstationcnt, 10) : null; // 남은 정류장
                var sec = (a.arrtime != null) ? parseInt(a.arrtime, 10) : null; // 남은 시간(초)
                var minutes = null; // 남은 시간(분)
                if (!isNaN(sec) && sec != null) minutes = Math.round(sec / 60.0); // 분 계산
                return angular.extend({}, a, { // 객체 확장
                    remainStops: isNaN(remainStops) ? null : remainStops,
                    remainMinutes: minutes
                });
            });
            $scope.arrivalList = mapped; // 목록 갱신
            drawBusesForArrivalRoutes($scope.arrivalList); // 버스 그리기
        }).catch(function (err) { // 에러 시
            console.error('도착 정보 조회 실패:', err); // 로그
            $scope.arrivalList = previousArrivalList; // 복원
        });
    }

    // [수정됨] 정류장 선택 함수 (줌인+이동 포함)
    $scope.selectStop = function (stop) { // 선택 함수
        if (!stop) return; // 정류장 없으면 중단
        $scope.selectedStop = stop; // 선택된 정류장 설정
        $scope.currentStop  = stop; // 현재 정류장 설정

        fetchArrivalsForCurrentStop(); // 도착 정보 조회
        
        // 1. 마커 색상 갱신
        drawStopsOnMap($scope.stops); // 정류장 다시 그리기

        // 2. 지도 이동 및 줌인
        if (olMap) { // 지도 있으면
            var lat = parseFloat(stop.gpslati || stop.gpsLati || stop.gpsY); // 위도
            var lon = parseFloat(stop.gpslong || stop.gpsLong || stop.gpsX); // 경도

            if (!isNaN(lat) && !isNaN(lon)) { // 유효성 검사
                var center = ol.proj.transform([lon, lat], 'EPSG:4326', 'EPSG:5179'); // 좌표 변환
                var view = olMap.getView(); // 뷰 획득
                if (view) { // 뷰 있으면
                    view.animate({ // 애니메이션
                        center: center, // 중심 이동
                        zoom: 17, // 줌 레벨 (확대)
                        duration: 500 // 지속 시간
                    });
                }
            }
        }
    };

    function cancelAutoRefresh() { // 자동고침 취소
        if (autoRefreshPromise) {
            $interval.cancel(autoRefreshPromise); // 취소
            autoRefreshPromise = null; // 초기화
        }
        $scope.isAutoRefreshOn = false; // 플래그 끔
    }

    function startAutoRefresh() { // 자동고침 시작
        cancelAutoRefresh(); // 기존 취소
        if ($scope.currentRouteId) { // 노선 모드면
            autoRefreshPromise = $interval(function () {
                $scope.fetchBusLocations(); // 위치 조회
            }, 10000); // 10초마다
            $scope.isAutoRefreshOn = true; // 플래그 켬
        } else if ($scope.selectedStop) { // 정류장 모드면
            autoRefreshPromise = $interval(function () {
                fetchArrivalsForCurrentStop(); // 도착 정보 조회
            }, 10000); // 10초마다
            $scope.isAutoRefreshOn = true; // 플래그 켬
        }
    }

    $scope.$on('$destroy', function () { // 소멸 시
        cancelAutoRefresh(); // 자동고침 취소
        clearRepPulse(); // 펄스 초기화
    });

    $scope.enableAutoRefresh = function () { // 자동고침 활성화
        if ($scope.currentRouteId || $scope.selectedStop) { // 조건 만족 시
            startAutoRefresh(); // 시작
        } else { // 아니면
            alert('먼저 버스 번호를 검색하거나 정류장을 선택하세요.'); // 경고
        }
    };

    $scope.disableAutoRefresh = function () { // 자동고침 비활성화
        cancelAutoRefresh(); // 취소
    };

    $scope.doSearch = function () { // 검색 함수
        if (!$scope.searchKeyword) { // 검색어 없으면
            alert('검색어를 입력하세요.'); // 경고
            return;
        }
        if ($scope.searchType === 'route') { // 노선 검색
            $scope.searchTerm = $scope.searchKeyword;
            $scope.searchBus();
        } else if ($scope.searchType === 'stop') { // 정류장 검색
            $scope.searchStops();
        } else { // 기타 (기본 노선)
            $scope.searchTerm = $scope.searchKeyword;
            $scope.searchBus();
        }
    };

    $scope.searchBus = function () { // 버스 노선 검색
        if (!$scope.searchTerm) { // 검색어 없으면
            alert('버스 번호를 입력하세요.');
            return;
        }
        var routeNo = $scope.searchTerm; // 노선번호
        cancelAutoRefresh(); // 자동고침 취소

        $http.get('/api/bus/routes', { params: { routeNo: routeNo } }) // API 호출
            .then(function (res) { // 성공 시
                $scope.routeResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2); // 결과 저장
                var data = parseMaybeJson(res.data); // 파싱
                if (!data || !data.response || !data.response.body) { // 유효성 검사
                    alert('노선 정보를 찾을 수 없습니다.');
                    return;
                }
                var items = data.response.body.items && data.response.body.items.item; // 아이템 추출
                if (!items) { // 아이템 없으면
                    alert('노선 목록이 비어 있습니다.');
                    return;
                }
                var first = angular.isArray(items) ? items[0] : items; // 첫 번째 항목
                var routeId = first.routeid || first.routeId; // 노선 ID
                if (!routeId) { // ID 없으면
                    alert('routeId 없음');
                    return;
                }

                $scope.currentRouteId = routeId; // ID 설정
                $scope.representativeBus = null; // 대표 버스 초기화
                $scope.prevStop = null; // 이전 정류장 초기화
                $scope.currentStop = null; // 현재 정류장 초기화
                $scope.nextStop = null; // 다음 정류장 초기화
                $scope.arrivalList = []; // 도착 목록 초기화
                $scope.selectedStop = null; // 선택된 정류장 초기화
                $scope.tempRouteIdFromStop = null; // 임시 ID 초기화
                lastRepVehicleNoForPan = null; // 이동 변수 초기화

                $scope.fetchRouteStops(routeId); // 정류장 조회
                $scope.fetchBusLocations(); // 위치 조회
                startAutoRefresh(); // 자동고침 시작
            }).catch(function (err) { // 에러 시
                console.error('노선 조회 실패:', err);
                alert('노선 정보를 가져오지 못했습니다.');
            });
    };

    $scope.fetchRouteStops = function (routeId) { // 노선 정류장 조회
        if (!routeId) return; // ID 없으면 중단
        $http.get('/api/bus/route-stops', { params: { routeId: routeId } }) // API 호출
            .then(function (res) { // 성공 시
                $scope.stopsResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2); // 결과 저장
                var data = parseMaybeJson(res.data); // 파싱
                if (!data || !data.response || !data.response.body) { // 유효성 검사
                    alert('정류장 정보를 찾을 수 없습니다.');
                    return;
                }
                var items = data.response.body.items && data.response.body.items.item; // 아이템 추출
                if (!items) { // 아이템 없으면
                    alert('정류장 목록이 비어 있습니다.');
                    return;
                }
                var stopsArray = angular.isArray(items) ? items : [items]; // 배열 변환
                $scope.stops = stopsArray; // 목록 저장
                $scope.selectedStop = null; // 선택 초기화

                drawStopsOnMap(stopsArray); // 정류장 그리기
                drawRouteLineFromStops(stopsArray); // 라인 그리기

                if ($scope.representativeBus) { // 대표 버스 있으면
                    var calc = computePrevCurrentNextForBus($scope.representativeBus, $scope.stops); // 계산
                    $scope.prevStop = calc.prev; // 이전 설정
                    $scope.currentStop = calc.current; // 현재 설정
                    $scope.nextStop = calc.next; // 다음 설정
                    fetchArrivalsForCurrentStop(); // 도착 정보 조회
                }
            }).catch(function (err) { // 에러 시
                console.error('정류장 목록 조회 실패:', err);
                alert('정류장 정보를 가져오지 못했습니다.');
            });
    };

    $scope.searchStops = function () { // 정류장 검색
        if (!$scope.searchKeyword) { // 검색어 없으면
            alert('정류장 이름을 입력하세요.');
            return;
        }
        var keyword = $scope.searchKeyword; // 검색어
        cancelAutoRefresh(); // 자동고침 취소

        $scope.currentRouteId = null; // 노선 ID 초기화
        $scope.representativeBus = null; // 대표 버스 초기화
        $scope.prevStop = null;
        $scope.currentStop = null;
        $scope.nextStop = null;
        $scope.arrivalList = [];
        $scope.selectedStop = null;
        $scope.tempRouteIdFromStop = null;

        clearRouteLine(); // 라인 지우기
        clearBusMarkers(); // 버스 지우기
        clearRepPulse(); // 펄스 지우기
        lastRepVehicleNoForPan = null; // 이동 변수 초기화
        hideHoverTooltip(); // 툴팁 숨김

        $scope.isMapLoading = true; // 로딩 시작

        $http.get('/api/bus/stops-by-name', { // API 호출
            params: { nodeName: keyword, pageNo: 1, numOfRows: 100 }
        }).then(function (res) { // 성공 시
            $scope.stopsResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2); // 결과 저장
            var data = parseMaybeJson(res.data); // 파싱
            if (!data || !data.response || !data.response.body) { // 유효성 검사
                $scope.stops = [];
                $scope.selectedStop = null;
                return;
            }
            var itemsRoot = data.response.body.items; // 루트
            if (!itemsRoot || !itemsRoot.item) { // 아이템 없으면
                $scope.stops = [];
                $scope.selectedStop = null;
                alert('검색된 정류장이 없습니다.');
                return;
            }
            var items = itemsRoot.item; // 아이템
            var rawStopsArray = angular.isArray(items) ? items : [items]; // 배열 변환
            var stopsArray = rawStopsArray.map(function (s) { // 매핑
                var id = s.nodeid || s.nodeId || s.node_id || s.nodeno || s.sttnId || s.stationId; // ID 추출
                return angular.extend({}, s, { nodeid: id }); // ID 추가
            });
            $scope.stops = stopsArray; // 목록 저장
            $scope.selectedStop = null; // 선택 초기화
            drawStopsOnMap(stopsArray); // 그리기
        }).catch(function (err) { // 에러 시
            console.error('정류장 검색 실패:', err);
            alert('정류장 정보를 가져오지 못했습니다.');
        }).finally(function () { // 완료 시
            $scope.isMapLoading = false; // 로딩 끝
        });
    };

    $scope.fetchBusLocations = function () { // 버스 위치 조회
        if (!$scope.currentRouteId) return; // 노선 ID 없으면 중단
        $scope.isMapLoading = true; // 로딩 시작

        $http.get('/api/bus/locations', { // API 호출
            params: { routeId: $scope.currentRouteId, pageNo: 1, numOfRows: 100 }
        }).then(function (res) { // 성공 시
            $scope.locationResultJson = angular.isString(res.data) ? res.data : JSON.stringify(res.data, null, 2); // 결과 저장
            var data = parseMaybeJson(res.data); // 파싱
            if (!data || !data.response || !data.response.body) { // 유효성 검사
                clearBusMarkers();
                $scope.representativeBus = null;
                clearRepPulse();
                return;
            }
            var items = data.response.body.items && data.response.body.items.item; // 아이템 추출
            if (!items) { // 아이템 없으면
                clearBusMarkers();
                $scope.representativeBus = null;
                clearRepPulse();
                return;
            }
            var busArray = angular.isArray(items) ? items : [items]; // 배열 변환
            var newRepresentative = null; // 새 대표 버스
            var oldRep = $scope.representativeBus; // 구 대표 버스

            if (oldRep && oldRep.vehicleno) { // 구 대표 있으면
                for (var i = 0; i < busArray.length; i++) {
                    var b = busArray[i];
                    if (b.vehicleno && b.vehicleno === oldRep.vehicleno) { // 차량번호 일치
                        newRepresentative = b; // 유지
                        break;
                    }
                }
            }
            if (!newRepresentative && busArray.length > 0) { // 없으면
                var idx = Math.floor(Math.random() * busArray.length); // 랜덤 선택
                newRepresentative = busArray[idx];
            }

            $scope.representativeBus = newRepresentative || null; // 대표 설정

            if ($scope.representativeBus) { // 대표 있으면
                panToRepresentativeBusIfNeeded($scope.representativeBus); // 이동
                updateRepPulseFeatureByBus($scope.representativeBus); // 펄스
            } else { // 없으면
                clearRepPulse(); // 펄스 초기화
            }

            if ($scope.representativeBus && $scope.stops && $scope.stops.length > 0) { // 정류장 있으면
                var calc2 = computePrevCurrentNextForBus($scope.representativeBus, $scope.stops); // 계산
                $scope.prevStop = calc2.prev;
                $scope.currentStop = calc2.current;
                $scope.nextStop = calc2.next;
                fetchArrivalsForCurrentStop(); // 도착 조회
            } else { // 없으면
                $scope.prevStop = null;
                $scope.currentStop = null;
                $scope.nextStop = null;
                $scope.arrivalList = [];
                $scope.selectedStop = null;
            }
            drawBusLocationsOnMap(busArray); // 버스 그리기
        }).catch(function (err) { // 에러 시
            console.error('버스 위치 조회 실패:', err);
            $scope.representativeBus = null;
            clearRepPulse();
        }).finally(function () { // 완료 시
            $scope.isMapLoading = false; // 로딩 끝
        });
    };
});