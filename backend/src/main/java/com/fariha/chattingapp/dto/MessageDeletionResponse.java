package com.fariha.chattingapp.dto;

public record MessageDeletionResponse(
        String messageId,
        String conversationId,
        String scope
) {
}
