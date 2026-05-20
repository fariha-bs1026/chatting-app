package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import jakarta.validation.Valid;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ConversationController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public List<ConversationDto> listConversations(@AuthenticationPrincipal UserAccount currentUser) {
        return chatService.listConversations(currentUser);
    }

    @PostMapping("/direct")
    public ConversationDto createDirectConversation(
            @Valid @RequestBody DirectConversationRequest request,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return chatService.createDirectConversation(request.userId(), currentUser);
    }

    @PostMapping("/groups")
    public ConversationDto createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return chatService.createGroup(request, currentUser);
    }

    @GetMapping("/{conversationId}")
    public ConversationDto getConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return chatService.getConversation(conversationId, currentUser);
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageDto> getMessages(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return chatService.getMessages(conversationId, currentUser);
    }

    @PostMapping("/{conversationId}/messages")
    public MessageDto sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        MessageDto message = chatService.sendMessage(
                new SendMessageRequest(conversationId, request.content(), request.type(), request.assetUrl()),
                currentUser
        );
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, message);
        return message;
    }
}
