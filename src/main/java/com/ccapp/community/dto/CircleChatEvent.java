package com.ccapp.community.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Événement WebSocket temps réel pour le chat de cercle.
 * eventType : NEW_MESSAGE | READ_RECEIPT
 */
public record CircleChatEvent(
        String eventType,
        Object payload
) {
    public static CircleChatEvent newMessage(CircleChatMessageResponse message) {
        return new CircleChatEvent("NEW_MESSAGE", message);
    }

    public static CircleChatEvent readReceipt(UUID messageId, UUID memberId, OffsetDateTime readAt) {
        return new CircleChatEvent("READ_RECEIPT", new ReadReceiptEvent(messageId, memberId, readAt));
    }

    public record ReadReceiptEvent(UUID messageId, UUID memberId, OffsetDateTime readAt) {}
}
