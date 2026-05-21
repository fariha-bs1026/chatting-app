package com.fariha.chattingapp.dto;

import java.time.Instant;
import java.util.List;

public record MessagePageResponse(
        List<MessageDto> messages,
        Instant nextBefore,
        boolean hasMore
) {
}
