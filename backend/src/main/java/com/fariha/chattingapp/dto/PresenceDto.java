package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.UserAccount;

import java.time.Instant;

public record PresenceDto(
        String userId,
        String username,
        boolean online,
        Instant lastSeenAt
) {
    public static PresenceDto from(UserAccount user) {
        return new PresenceDto(user.getId(), user.getUsername(), user.isOnline(), user.getLastSeenAt());
    }
}
