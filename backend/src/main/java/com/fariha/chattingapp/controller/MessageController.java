package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import jakarta.validation.Valid;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @PatchMapping("/{messageId}/status")
    public MessageDto updateStatus(
            @PathVariable String messageId,
            @Valid @RequestBody MessageStatusRequest request,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        MessageDto message = chatService.updateMessageStatus(
                messageId,
                ChatService.parseStatus(request.status()),
                currentUser
        );
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + message.conversationId() + "/status",
                new MessageStatusEvent(message.id(), message.conversationId(), message.status())
        );
        return message;
    }
}
