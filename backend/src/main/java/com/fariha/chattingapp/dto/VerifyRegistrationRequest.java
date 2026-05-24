package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRegistrationRequest(
        @NotBlank(message = "{auth.verification-id.required}")
        String verificationId,

        @NotBlank(message = "{auth.verification-code.required}")
        @Pattern(regexp = "^\\d{6}$", message = "{auth.verification-code.invalid}")
        String code
) {
}
