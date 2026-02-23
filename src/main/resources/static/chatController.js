app.controller('ChatController', function ($scope, $http, $timeout, $rootScope) {
    $scope.isOpen = false;
    $scope.messages = [];
    $scope.userMessage = '';
    $scope.isTyping = false;

    // Load messages from localStorage if desired, currently empty
    // $scope.messages = JSON.parse(localStorage.getItem('chatHistory')) || [];

    $scope.toggleChat = function () {
        $scope.isOpen = !$scope.isOpen;
        // Auto-scroll to bottom directly
        if ($scope.isOpen) {
            $scope.scrollToBottom();
        }
    };

    $scope.sendMessage = function () {
        if (!$scope.userMessage.trim()) return;

        // Add user message
        const msg = { text: $scope.userMessage, type: 'user' };
        $scope.messages.push(msg);
        const userMsg = $scope.userMessage;
        $scope.userMessage = '';
        $scope.isTyping = true;
        $scope.scrollToBottom();

        // Call API
        // Send last 20 messages for context (increased to remember more history)
        const history = $scope.messages
            .slice(-21, -1) // Get up to 20 previous messages (20+1 because slice end is exclusive)
            .map(m => ({ role: m.type === 'user' ? 'user' : 'model', text: m.text }));

        $http.post('/api/chat', { 
            message: userMsg,
            history: history 
        })
            .then(function (response) {
                $scope.isTyping = false;
                const data = response.data;
                
                // Add bot message
                $scope.messages.push({ text: data.text, type: 'bot' });
                
                // Handle Path Visualization
                if (data.path) {
                    console.log('[Chat] Received path data:', data.path);
                    $rootScope.$broadcast('DRAW_CHAT_PATH', data.path);
                }
                
                // [New] Handle Station Visualization
                if (data.station) {
                    console.log('[Chat] Received station data:', data.station);
                    $rootScope.$broadcast('DRAW_CHAT_STATION', data.station);
                }

                $scope.scrollToBottom();
            })
            .catch(function (error) {
                $scope.isTyping = false;
                $scope.messages.push({ text: "죄송합니다. 오류가 발생했습니다.", type: 'bot' });
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
