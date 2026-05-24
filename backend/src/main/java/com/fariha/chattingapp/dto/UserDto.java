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
        return from(user, user.getAvatarUrl());
    }

    public static UserDto from(UserAccount user, String avatarUrl) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                avatarUrl,
                user.isOnline(),
                user.getLastSeenAt()
        );
    }
}
