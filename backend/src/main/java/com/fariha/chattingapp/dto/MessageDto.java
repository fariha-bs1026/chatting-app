package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;


import java.time.Instant;

public record MessageDto(
        String id,
        String conversationId,
        UserDto sender,
        String content,
        String assetUrl,
        String type,
        String status,
        Instant createdAt
) {
    public static MessageDto from(ChatMessage message, UserAccount sender) {
        return new MessageDto(
                message.getId(),
                message.getConversationId(),
                UserDto.from(sender),
                message.getContent(),
                message.getAssetUrl(),
                message.getType().name(),
                message.getStatus().name(),
                message.getCreatedAt()
        );
    }
}
