package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.UserAccount;

import java.time.Instant;

public record CurrentUserDto(
        String id,
        String username,
        String displayName,
        String phoneNumber,
        String avatarUrl,
        boolean online,
        Instant lastSeenAt
) {
    public static CurrentUserDto from(UserAccount user, String avatarUrl) {
        return new CurrentUserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPhoneNumber(),
                avatarUrl,
                user.isOnline(),
                user.getLastSeenAt()
        );
    }
}
