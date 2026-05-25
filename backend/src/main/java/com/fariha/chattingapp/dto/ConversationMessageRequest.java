package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record ConversationMessageRequest(
        @Size(max = 4000) String content,
        @Pattern(regexp = "(?i)^(TEXT|IMAGE|AUDIO|VIDEO|FILE)$", message = "{message.type.invalid}") String type,
        @Size(max = 1000) String assetUrl,
        @Size(max = 500) String assetKey,
        @Min(value = 1, message = "{message.expires.min}")
        @Max(value = 604800, message = "{message.expires.max}")
        Integer expiresInSeconds
) {
}
