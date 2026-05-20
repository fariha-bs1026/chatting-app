package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotNull String conversationId,
        @Size(max = 4000) String content,
        String type,
        @Size(max = 1000) String assetUrl
) {
}
