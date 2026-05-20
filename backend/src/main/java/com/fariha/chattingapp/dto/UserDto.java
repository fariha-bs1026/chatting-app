package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import java.time.Instant;

public record UserDto(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        boolean online,
        Instant lastSeenAt
) {
    public static UserDto from(UserAccount user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.isOnline(),
                user.getLastSeenAt()
        );
    }
}
