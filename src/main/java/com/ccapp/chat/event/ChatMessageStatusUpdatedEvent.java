package com.ccapp.chat.event;

import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageStatusUpdatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID messageId,
        UUID conversationId,
        ChatMessageStatus status,
        UUID recipientAccountHolderId
) implements DomainEvent {

    public static ChatMessageStatusUpdatedEvent create(UUID messageId,
                                                       UUID conversationId,
                                                       ChatMessageStatus status,
                                                       UUID recipientAccountHolderId) {
        return new ChatMessageStatusUpdatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                messageId,
                conversationId,
                status,
                recipientAccountHolderId
        );
    }
}
