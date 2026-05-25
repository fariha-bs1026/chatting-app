package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotBlank;

public record TypingEvent(
        @NotBlank(message = "{conversation.id.required}")
        String conversationId,
        String username,
        boolean typing
) {
}
