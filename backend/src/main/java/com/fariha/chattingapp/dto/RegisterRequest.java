package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "{auth.username.required}")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$", message = "{auth.username.invalid}")
        String username,

        @NotBlank(message = "{auth.display-name.required}")
        @Size(min = 2, max = 120, message = "{auth.display-name.size}")
        String displayName,

        @NotBlank(message = "{auth.phone.required}")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "{auth.phone.invalid}")
        String phoneNumber,

        @NotBlank(message = "{auth.password.required}")
        @Size(min = 6, max = 100, message = "{auth.password.size}")
        String password
) {
}
