package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

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

    public ChatSocketController(
            ChatService chatService,
            UserAccountRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatService = chatService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload SendMessageRequest request, Principal principal) {
        UserAccount sender = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow();
        MessageDto message = chatService.sendMessage(request, sender);
        messagingTemplate.convertAndSend("/topic/conversations/" + request.conversationId(), message);
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
                "/topic/conversations/" + message.conversationId() + "/status",
                new MessageStatusEvent(message.id(), message.conversationId(), message.status())
        );
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingEvent event, Principal principal) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow();
        if (!chatService.isParticipant(event.conversationId(), user.getId())) {
            throw new AccessDeniedException("You are not a participant in this conversation");
        }
        TypingEvent outgoing = new TypingEvent(event.conversationId(), principal.getName(), event.typing());
        messagingTemplate.convertAndSend("/topic/conversations/" + event.conversationId() + "/typing", outgoing);
    }
}
