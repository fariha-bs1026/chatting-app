package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;

public record TypingEvent(
        @NotBlank(message = "{conversation.id.required}")
        String conversationId,
        String username,
        boolean typing
) {
}
