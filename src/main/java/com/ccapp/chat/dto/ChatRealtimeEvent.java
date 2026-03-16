package com.ccapp.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatRealtimeEvent(
        String eventType,
        UUID conversationId,
        OffsetDateTime emittedAt,
        ChatMessageResponse message
) {
}
