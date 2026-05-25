package com.fariha.chattingapp.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateGroupRequest(
        @NotBlank(message = "{group.name.required}") @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotEmpty(message = "{group.members.required}") @Size(max = 100) Set<@NotBlank String> memberIds
) {
}
