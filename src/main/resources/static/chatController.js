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
        $http.post('/api/chat', { message: userMsg })
            .then(function (response) {
                $scope.isTyping = false;
                const data = response.data;
                
                // Add bot message
                $scope.messages.push({ text: data.text, type: 'bot' });
                
                // Handle Path Visualization
                if (data.path) {
                    console.log('[Chat] Received path data:', data.path);
                    // Emit event to BusController (sibling controller)
                    // We use $rootScope to broadcast since they are siblings or $rootScope.$emit
                    // Or if they are in same scope hierarchy. Usually safer to use $rootScope.$broadcast
                    $rootScope.$broadcast('DRAW_CHAT_PATH', data.path);
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
