/**
 * 3-3. BusController: 버스 정보 조회 영역을 제어합니다.
 * 이 컨트롤러는 어떤 페이지로 이동하든 항상 활성화 상태를 유지합니다.
 */
app.controller('BusController', function ($scope, $http) {
    $scope.allBusStops = [];
    $scope.searchTerm = '';
    $scope.resultText = '버튼을 눌러 데이터를 불러오세요.';

    $scope.fetchData = function () {
        $scope.resultText = '데이터를 불러오는 중...';
        var url = `http://localhost:8080/api/bus-stops`;
        $http
            .get(url)
            .then(function (response) {
                const data = response.data;
                if (typeof data === 'string' && data.trim().startsWith('<')) {
                    const errorCodeMatch = data.match(/<returnReasonCode>(\d+)<\/returnReasonCode>/);
                    const errorMsgMatch = data.match(/<returnAuthMsg>(.*?)<\/returnAuthMsg>/);
                    let errorMessage = '외부 API에서 오류가 발생했습니다.';
                    if (errorCodeMatch && errorMsgMatch) {
                        errorMessage = `API 오류: ${errorMsgMatch[1]} (코드: ${errorCodeMatch[1]})`;
                    }
                    $scope.resultText = errorMessage;
                } else if (data && data.body && data.body.items) {
                    $scope.allBusStops = data.body.items.bs;
                    $scope.resultText = `총 ${$scope.allBusStops.length}개 정류장 데이터 로딩 완료!`;
                } else {
                    $scope.resultText = '데이터를 받았지만 형식이 올바르지 않습니다.';
                }
            })
            .catch(function (errorResponse) {
                $scope.resultText = `API 요청 실패: (상태: ${errorResponse.status})`;
            });
    };

    $scope.searchBusStops = function () {
        if ($scope.allBusStops.length == 0) {
            $scope.resultText = '먼저 "데이터 요청하기" 버튼을 눌러주세요.';
            return;
        }
        if (!$scope.searchTerm) {
            $scope.resultText = '검색할 정류장 이름을 입력해주세요.';
            return;
        }
        const filteredStops = $scope.allBusStops.filter(function (stop) {
            return stop.bsNm.includes($scope.searchTerm);
        });

        if (filteredStops.length > 0) {
            const formattedResult = filteredStops
                .map(function (stop) {
                    return `정류장 이름: ${stop.bsNm}\n정류장 ID: ${stop.bsId}\n좌표: (${stop.xPos}, ${stop.yPos})\n`;
                })
                .join('\n');
            $scope.resultText = formattedResult;
        } else {
            $scope.resultText = `'${$scope.searchTerm}' 이름이 포함된 정류장을 찾을 수 없습니다.`;
        }
    };
});
