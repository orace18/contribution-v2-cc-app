package com.ccapp.chat.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessagesPageResponse(
        UUID conversationId,
        List<ChatMessageResponse> messages,
        OffsetDateTime nextBefore
) {
}
