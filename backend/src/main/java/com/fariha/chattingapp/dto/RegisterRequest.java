package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$", message = "Username must be 3-50 characters and contain only letters, numbers, dot, dash, or underscore")
        String username,

        @NotBlank
        @Size(min = 2, max = 120)
        String displayName,

        @NotBlank
        @Size(min = 6, max = 100)
        String password
) {
}
