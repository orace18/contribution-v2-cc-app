package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleJoinedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID circleId
) implements DomainEvent {

    public static CircleJoinedEvent create(UUID accountHolderId, UUID circleId) {
        return new CircleJoinedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId, circleId);
    }
}
