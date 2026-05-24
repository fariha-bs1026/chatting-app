package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@Validated
public class ConversationController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationUpdateBroadcaster conversationUpdateBroadcaster;

    public ConversationController(
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate,
            ConversationUpdateBroadcaster conversationUpdateBroadcaster
    ) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.conversationUpdateBroadcaster = conversationUpdateBroadcaster;
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
        ConversationDto conversation = chatService.createGroup(request, currentUser);
        conversationUpdateBroadcaster.broadcastConversation(conversation.id());
        return conversation;
    }

    @GetMapping("/{conversationId}")
    public ConversationDto getConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return chatService.getConversation(conversationId, currentUser);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        chatService.deleteConversationForUser(conversationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePageResponse getMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) Instant before,
            @RequestParam(required = false)
            @Min(value = 1, message = "{message.limit.min}")
            @Max(value = 100, message = "{message.limit.max}")
            Integer limit,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return chatService.getMessages(conversationId, before, limit, currentUser);
    }

    @PostMapping("/{conversationId}/messages")
    public MessageDto sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody ConversationMessageRequest request,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        MessageDto message = chatService.sendMessage(
                new SendMessageRequest(
                        conversationId,
                        request.content(),
                        request.type(),
                        request.assetUrl(),
                        request.assetKey(),
                        request.expiresInSeconds()
                ),
                currentUser
        );
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, message);
        conversationUpdateBroadcaster.broadcastConversation(conversationId);
        return message;
    }
}
