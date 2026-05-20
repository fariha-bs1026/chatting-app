package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

public record TypingEvent(
        String conversationId,
        String username,
        boolean typing
) {
}
