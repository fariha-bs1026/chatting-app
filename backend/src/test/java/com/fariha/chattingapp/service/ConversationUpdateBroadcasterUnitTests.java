package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.ConversationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationUpdateBroadcasterUnitTests {
    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ConversationUpdateBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new ConversationUpdateBroadcaster(chatService, messagingTemplate);
    }

    @Test
    void broadcastConversationSendsEachViewerSpecificConversationToUserQueue() {
        ConversationDto aliceConversation = conversation("conversation-1");
        ConversationDto bobConversation = conversation("conversation-1");
        when(chatService.conversationBroadcasts("conversation-1")).thenReturn(List.of(
                new ChatService.ConversationBroadcast("alice", aliceConversation),
                new ChatService.ConversationBroadcast("bob", bobConversation)
        ));

        broadcaster.broadcastConversation("conversation-1");

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/conversations", aliceConversation);
        verify(messagingTemplate).convertAndSendToUser("bob", "/queue/conversations", bobConversation);
    }

    @Test
    void broadcastParticipantConversationsUsesVisibleConversationUpdates() {
        ConversationDto conversation = conversation("conversation-2");
        when(chatService.visibleConversationBroadcastsForParticipant("user-1")).thenReturn(List.of(
                new ChatService.ConversationBroadcast("alice", conversation)
        ));

        broadcaster.broadcastParticipantConversations("user-1");

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/conversations", conversation);
    }

    private static ConversationDto conversation(String id) {
        return new ConversationDto(id, true, null, null, List.of(), null, 0, null, null);
    }
}
