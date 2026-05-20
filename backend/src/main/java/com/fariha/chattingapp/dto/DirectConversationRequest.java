package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotNull;

public record DirectConversationRequest(@NotNull String userId) {
}
