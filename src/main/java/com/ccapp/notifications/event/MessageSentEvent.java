package com.ccapp.notifications.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageSentEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID messageId,
        UUID senderAccountHolderId,
        UUID recipientAccountHolderId,
        String channel
) implements DomainEvent {

    public static MessageSentEvent create(UUID messageId,
                                          UUID senderAccountHolderId,
                                          UUID recipientAccountHolderId,
                                          String channel) {
        return new MessageSentEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                messageId,
                senderAccountHolderId,
                recipientAccountHolderId,
                channel
        );
    }
}
