app.controller('ChatController', function ($scope, $http, $timeout, $rootScope) {
    $scope.isOpen = false;
    $scope.messages = [];
    $scope.userMessage = '';
    $scope.isTyping = false;

    // LocalStorage에서 대화 기록 불러오기 (선택 사항)
    // $scope.messages = JSON.parse(localStorage.getItem('chatMessages')) || [];

    $scope.toggleChat = function () {
        $scope.isOpen = !$scope.isOpen;
        // 오토 스크롤: 채팅창이 열릴 때 마지막 메시지로 스크롤
        if ($scope.isOpen) {
            $scope.scrollToBottom();
        }
    };

    $scope.sendMessage = function () {
        if (!$scope.userMessage.trim()) return;

        // 유저 메시지 추가
        const msg = { text: $scope.userMessage, type: 'user' };
        $scope.messages.push(msg);
        const userMsg = $scope.userMessage;
        $scope.userMessage = '';
        $scope.isTyping = true;
        $scope.scrollToBottom();

        // Call API
        // 보낸 메시지와 최근 20개의 대화 기록을 함께 전송
        const history = $scope.messages
            .slice(-21, -1) //  최근 20개의 메세지
            .map((m) => ({ role: m.type === 'user' ? 'user' : 'model', text: m.text }));

        $http
            .post('/api/chat', {
                message: userMsg,
                history: history,
            })
            .then(function (response) {
                $scope.isTyping = false;
                const data = response.data;

                // 봇 메시지 추가
                $scope.messages.push({ text: data.text, type: 'bot' });

                // 핸들링: 경로 시각화
                if (data.path) {
                    console.log('[Chat] Received path data:', data.path);
                    $rootScope.$broadcast('DRAW_CHAT_PATH', data.path);
                }

                // 핸들링: 역 시각화
                if (data.station) {
                    console.log('[Chat] Received station data:', data.station);
                    $rootScope.$broadcast('DRAW_CHAT_STATION', data.station);
                }

                $scope.scrollToBottom();
            })
            .catch(function (error) {
                $scope.isTyping = false;
                $scope.messages.push({ text: '죄송합니다. 오류가 발생했습니다.', type: 'bot' });
                console.error('[Chat] API Error:', error);
                $scope.scrollToBottom();
            });
    };

    $scope.scrollToBottom = function () {
        $timeout(function () {
            const chatMessages = document.querySelector('.chat-messages');
            if (chatMessages) {
                chatMessages.scrollTop = chatMessages.scrollHeight;
            }
        }, 0);
    };
});
