package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KycVerifiedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId
) implements DomainEvent {

    public static KycVerifiedEvent create(UUID accountHolderId) {
        return new KycVerifiedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId);
    }
}
