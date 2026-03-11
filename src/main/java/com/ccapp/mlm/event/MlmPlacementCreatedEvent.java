package com.ccapp.mlm.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MlmPlacementCreatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID sponsorAccountHolderId,
        UUID placedUnderAccountHolderId
) implements DomainEvent {

    public static MlmPlacementCreatedEvent create(UUID accountHolderId,
                                                  UUID sponsorAccountHolderId,
                                                  UUID placedUnderAccountHolderId) {
        return new MlmPlacementCreatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                accountHolderId,
                sponsorAccountHolderId,
                placedUnderAccountHolderId
        );
    }
}
