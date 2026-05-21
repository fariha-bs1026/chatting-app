package com.fariha.chattingapp.dto;

import java.time.Instant;

public record RegistrationStartResponse(
        String verificationId,
        Instant expiresAt,
        String debugCode
) {
}
