package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(
        @Size(max = 4000) String content,
        String type,
        @Size(max = 1000) String assetUrl
) {
}
