package com.ccapp.chat.dto;

import com.ccapp.chat.domain.enums.ChatConversationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatConversationSummaryResponse(
        UUID conversationId,
        ChatConversationType type,
        String title,
        String avatar,
        String lastMessage,
        OffsetDateTime lastActivity,
        long unreadCount
) {
}
