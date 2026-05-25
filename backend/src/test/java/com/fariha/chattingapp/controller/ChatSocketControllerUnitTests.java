package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.TestIds;
import com.fariha.chattingapp.dto.CallSignalEvent;
import com.fariha.chattingapp.dto.TypingEvent;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.UserAccountRepository;
import com.fariha.chattingapp.service.ChatService;
import com.fariha.chattingapp.service.ConversationUpdateBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSocketControllerUnitTests {
    private static final Principal ALICE_PRINCIPAL = () -> "alice";

    @Mock
    private ChatService chatService;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ConversationUpdateBroadcaster conversationUpdateBroadcaster;

    private ChatSocketController controller;
    private UserAccount alice;

    @BeforeEach
    void setUp() {
        controller = new ChatSocketController(
                chatService,
                userRepository,
                messagingTemplate,
                conversationUpdateBroadcaster
        );
        alice = TestIds.withId(new UserAccount("alice", "Alice", "hash"), "alice-id");
    }

    @Test
    void typingBroadcastsAuthenticatedUsernameWhenUserIsParticipant() {
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(chatService.isParticipant("conversation-1", "alice-id")).thenReturn(true);

        controller.typing(new TypingEvent("conversation-1", "ignored", true), ALICE_PRINCIPAL);

        verify(messagingTemplate).convertAndSend(
                "/topic/conversations/conversation-1/typing",
                new TypingEvent("conversation-1", "alice", true)
        );
    }

    @Test
    void typingRejectsUsersOutsideTheConversation() {
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(chatService.isParticipant("conversation-1", "alice-id")).thenReturn(false);

        assertThatThrownBy(() -> controller.typing(
                new TypingEvent("conversation-1", "ignored", true),
                ALICE_PRINCIPAL
        )).isInstanceOf(AccessDeniedException.class);

        verify(messagingTemplate, never()).convertAndSend(
                "/topic/conversations/conversation-1/typing",
                new TypingEvent("conversation-1", "alice", true)
        );
    }

    @Test
    void callSignalBroadcastsWithAuthenticatedSenderUsername() {
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(chatService.isParticipant("conversation-1", "alice-id")).thenReturn(true);
        CallSignalEvent event = new CallSignalEvent("conversation-1", "call-1", "OFFER", "VIDEO", "{}");

        controller.callSignal(event, ALICE_PRINCIPAL);

        verify(messagingTemplate).convertAndSend(
                "/topic/conversations/conversation-1/calls",
                event.withSender("alice")
        );
    }
}
