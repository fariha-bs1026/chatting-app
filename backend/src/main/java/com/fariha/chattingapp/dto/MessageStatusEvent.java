package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.entity.*;

public record MessageStatusEvent(
        String messageId,
        String conversationId,
        String status
) {
}
