package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotBlank;

public record DirectConversationRequest(@NotBlank(message = "{user.id.required}") String userId) {
}
