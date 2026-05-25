package com.fariha.chattingapp.dto;

import java.time.Instant;
import java.util.List;

public record ConversationDto(
        String id,
        boolean direct,
        String name,
        String description,
        List<UserDto> participants,
        MessageDto lastMessage,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
}
