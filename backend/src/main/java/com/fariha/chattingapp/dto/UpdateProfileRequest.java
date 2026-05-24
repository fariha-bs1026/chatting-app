package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "{auth.display-name.required}")
        @Size(min = 2, max = 120, message = "{auth.display-name.size}")
        String displayName,

        @NotBlank(message = "{auth.phone.required}")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "{auth.phone.invalid}")
        String phoneNumber,

        @Size(max = 512, message = "{profile.avatar-key.size}")
        String avatarKey,

        boolean removeAvatar
) {
}
