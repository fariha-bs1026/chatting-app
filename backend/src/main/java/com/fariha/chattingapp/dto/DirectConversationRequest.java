package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;

public record DirectConversationRequest(@NotBlank(message = "{user.id.required}") String userId) {
}
