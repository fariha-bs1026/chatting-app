package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;


public record AuthResponse(String token, UserDto user) {
}
