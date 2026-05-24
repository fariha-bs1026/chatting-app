package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "{auth.username.required}") String username,
        @NotBlank(message = "{auth.password.required}") String password
) {
}
