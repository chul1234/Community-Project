/**
 * 3-3. BusController: 버스 정보(지도 + 검색 입력란) 화면 제어
 */
app.controller('BusController', function ($scope, $timeout) {

    // 검색어 (입력칸에 바인딩)
    $scope.searchTerm = '';

    // NGII 지도 객체
    $scope.map1 = null;

    /**
     * 지도 초기화
     * - bus-info.html 의 <div id="map1"> 안에 지도를 띄운다.
     */
    $scope.initMap = function () {
        var mapDiv = document.getElementById('map1');

        if (!window.ngii_wmts || !mapDiv) {
            console.error('NGII 지도 스크립트 또는 map1 요소를 찾을 수 없습니다.');
            return;
        }

        // NGII WMTS 지도 생성
        $scope.map1 = new ngii_wmts.map('map1', {
            zoom: 2  // 기본 줌 레벨 (필요하면 조절)
        });
    };

    // Angular에서 뷰가 로드된 후 지도 초기화
    $timeout($scope.initMap, 0);

    /**
     * 검색 버튼 클릭 시 호출되는 함수
     * - 1단계에서는 실제 검색 기능 없이, 형태만 유지
     * - 2단계(대전 실시간 버스)에서 실제 동작을 붙일 예정
     */
    $scope.searchBusStops = function () {
        if (!$scope.searchTerm) {
            alert('검색어를 입력해주세요.');
            return;
        }

        // 1단계에서는 아직 지도 이동/검색 기능 없음
        // 나중에: 대전 버스 실시간 위치 / 좌표 연동 시 여기 구현
        console.log('검색 요청:', $scope.searchTerm);
    };
});
