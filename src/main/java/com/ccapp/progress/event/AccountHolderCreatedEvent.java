package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountHolderCreatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId
) implements DomainEvent {

    public static AccountHolderCreatedEvent create(UUID accountHolderId) {
        return new AccountHolderCreatedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId);
    }
}
