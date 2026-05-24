package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MessageStatusRequest(
        @NotBlank(message = "{message.status.required}")
        @Pattern(regexp = "(?i)^(SENT|DELIVERED|READ)$", message = "{message.status.invalid}")
        String status
) {
}
