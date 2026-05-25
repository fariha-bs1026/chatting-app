package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CallSignalEvent(
        @NotBlank(message = "{conversation.id.required}") String conversationId,
        @NotBlank String callId,
        @Pattern(regexp = "(?i)^(OFFER|ANSWER|ICE|END|REJECT)$") String type,
        @Pattern(regexp = "(?i)^(AUDIO|VIDEO)$") String mode,
        @Size(max = 20000) String payload,
        String senderUsername
) {
    public CallSignalEvent(String conversationId, String callId, String type, String mode, String payload) {
        this(conversationId, callId, type, mode, payload, null);
    }

    public CallSignalEvent withSender(String username) {
        return new CallSignalEvent(conversationId, callId, type, mode, payload, username);
    }
}
