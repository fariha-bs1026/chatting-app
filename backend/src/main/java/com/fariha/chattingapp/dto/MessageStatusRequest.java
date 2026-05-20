package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

import jakarta.validation.constraints.NotBlank;

public record MessageStatusRequest(@NotBlank String status) {
}
