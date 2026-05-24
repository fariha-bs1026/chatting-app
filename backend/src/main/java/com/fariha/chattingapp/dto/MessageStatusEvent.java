package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MessageStatusEvent(
        @NotBlank(message = "{message.id.required}")
        String messageId,
        @NotBlank(message = "{conversation.id.required}")
        String conversationId,
        @NotBlank(message = "{message.status.required}")
        @Pattern(regexp = "(?i)^(SENT|DELIVERED|READ)$", message = "{message.status.invalid}")
        String status
) {
}
