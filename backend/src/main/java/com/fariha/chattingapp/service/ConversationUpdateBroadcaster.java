package com.fariha.chattingapp.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationUpdateBroadcaster {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ConversationUpdateBroadcaster(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastConversation(String conversationId) {
        chatService.conversationBroadcasts(conversationId).forEach(this::send);
    }

    public void broadcastParticipantConversations(String userId) {
        chatService.visibleConversationBroadcastsForParticipant(userId).forEach(this::send);
    }

    private void send(ChatService.ConversationBroadcast update) {
        messagingTemplate.convertAndSendToUser(
                update.username(),
                "/queue/conversations",
                update.conversation()
        );
    }
}
