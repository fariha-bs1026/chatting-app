package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.config.WebSocketDestinations;
import com.fariha.chattingapp.dto.CallSignalEvent;
import com.fariha.chattingapp.dto.MessageDto;
import com.fariha.chattingapp.dto.MessageStatusEvent;
import com.fariha.chattingapp.dto.SendMessageRequest;
import com.fariha.chattingapp.dto.TypingEvent;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.UserAccountRepository;
import com.fariha.chattingapp.service.ChatService;
import com.fariha.chattingapp.service.ConversationUpdateBroadcaster;

import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatSocketController {
    private final ChatService chatService;
    private final UserAccountRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationUpdateBroadcaster conversationUpdateBroadcaster;

    public ChatSocketController(
            ChatService chatService,
            UserAccountRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            ConversationUpdateBroadcaster conversationUpdateBroadcaster
    ) {
        this.chatService = chatService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.conversationUpdateBroadcaster = conversationUpdateBroadcaster;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload SendMessageRequest request, Principal principal) {
        UserAccount sender = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow();
        MessageDto message = chatService.sendMessage(request, sender);
        messagingTemplate.convertAndSend(WebSocketDestinations.conversation(request.conversationId()), message);
        conversationUpdateBroadcaster.broadcastConversation(message.conversationId());
    }

    @MessageMapping("/message.status")
    public void updateStatus(@Valid @Payload MessageStatusEvent event, Principal principal) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow();
        MessageDto message = chatService.updateMessageStatus(
                event.messageId(),
                ChatService.parseStatus(event.status()),
                user
        );
        messagingTemplate.convertAndSend(
                WebSocketDestinations.conversationStatus(message.conversationId()),
                new MessageStatusEvent(
                        message.id(),
                        message.conversationId(),
                        chatService.messageAggregateStatus(message.id()).name()
                )
        );
        conversationUpdateBroadcaster.broadcastConversation(message.conversationId());
    }

    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload TypingEvent event, Principal principal) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow();
        if (!chatService.isParticipant(event.conversationId(), user.getId())) {
            throw new AccessDeniedException("You are not a participant in this conversation");
        }
        TypingEvent outgoing = new TypingEvent(event.conversationId(), principal.getName(), event.typing());
        messagingTemplate.convertAndSend(WebSocketDestinations.conversationTyping(event.conversationId()), outgoing);
    }

    @MessageMapping("/call.signal")
    public void callSignal(@Valid @Payload CallSignalEvent event, Principal principal) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow();
        if (!chatService.isParticipant(event.conversationId(), user.getId())) {
            throw new AccessDeniedException("You are not a participant in this conversation");
        }
        messagingTemplate.convertAndSend(
                WebSocketDestinations.conversationCalls(event.conversationId()),
                event.withSender(principal.getName())
        );
    }
}
