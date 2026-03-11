package com.ccapp.chat.dto;

import com.ccapp.chat.domain.enums.ChatMessageStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID senderAccountHolderId,
        UUID recipientAccountHolderId,
        UUID conversationId,
        String content,
        String channel,
        String clientMessageId,
        ChatMessageStatus messageStatus,
        OffsetDateTime deliveredAt,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}
