package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        Set<String> memberIds
) {
}
