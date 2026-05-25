package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.ChatMessage;
import com.fariha.chattingapp.entity.UserAccount;

import java.time.Instant;

public record MessageDto(
        String id,
        String conversationId,
        UserDto sender,
        String content,
        String assetKey,
        String assetUrl,
        String assetContentType,
        String type,
        String status,
        boolean deletedForEveryone,
        boolean expired,
        Instant deletedAt,
        Instant expiresAt,
        Instant createdAt
) {
    public static MessageDto from(ChatMessage message, UserAccount sender) {
        return from(message, sender, message.getAssetUrl());
    }

    public static MessageDto from(ChatMessage message, UserAccount sender, String assetUrl) {
        return from(message, sender, assetUrl, sender.getAvatarUrl());
    }

    public static MessageDto from(ChatMessage message, UserAccount sender, String assetUrl, String senderAvatarUrl) {
        return from(message, sender, assetUrl, senderAvatarUrl, message.getStatus().name());
    }

    public static MessageDto from(
            ChatMessage message,
            UserAccount sender,
            String assetUrl,
            String senderAvatarUrl,
            String status
    ) {
        return new MessageDto(
                message.getId(),
                message.getConversationId(),
                UserDto.from(sender, senderAvatarUrl),
                message.getContent(),
                message.getAssetKey(),
                assetUrl,
                message.getAssetContentType(),
                message.getType().name(),
                status,
                message.isDeletedForEveryone(),
                message.isExpired(),
                message.getDeletedAt(),
                message.getExpiresAt(),
                message.getCreatedAt()
        );
    }
}
