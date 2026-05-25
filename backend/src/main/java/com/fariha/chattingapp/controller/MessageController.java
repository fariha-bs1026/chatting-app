package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.config.WebSocketDestinations;
import com.fariha.chattingapp.dto.MessageDeletionResponse;
import com.fariha.chattingapp.dto.MessageDto;
import com.fariha.chattingapp.dto.MessageStatusEvent;
import com.fariha.chattingapp.dto.MessageStatusRequest;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.service.ChatService;
import com.fariha.chattingapp.service.ConversationUpdateBroadcaster;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationUpdateBroadcaster conversationUpdateBroadcaster;

    public MessageController(
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate,
            ConversationUpdateBroadcaster conversationUpdateBroadcaster
    ) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.conversationUpdateBroadcaster = conversationUpdateBroadcaster;
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
                WebSocketDestinations.conversationStatus(message.conversationId()),
                new MessageStatusEvent(
                        message.id(),
                        message.conversationId(),
                        chatService.messageAggregateStatus(message.id()).name()
                )
        );
        conversationUpdateBroadcaster.broadcastConversation(message.conversationId());
        return message;
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Object> deleteMessage(
            @PathVariable String messageId,
            @RequestParam(defaultValue = "ME") String scope,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        String normalizedScope = scope.trim().toUpperCase(Locale.ROOT);
        if ("EVERYONE".equals(normalizedScope)) {
            MessageDto message = chatService.deleteMessageForEveryone(messageId, currentUser);
            messagingTemplate.convertAndSend(WebSocketDestinations.conversation(message.conversationId()), message);
            conversationUpdateBroadcaster.broadcastConversation(message.conversationId());
            return ResponseEntity.ok(message);
        }
        if (!"ME".equals(normalizedScope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported delete scope");
        }

        MessageDeletionResponse response = chatService.deleteMessageForMe(messageId, currentUser);
        return ResponseEntity.ok(response);
    }
}
