package com.ccapp.mlm.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MlmVolumeUpdatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID sourceMemberId,
        BigDecimal businessVolume,
        String currency,
        int entriesWritten
) implements DomainEvent {

    public static MlmVolumeUpdatedEvent create(UUID orderId,
                                               UUID sourceMemberId,
                                               BigDecimal businessVolume,
                                               String currency,
                                               int entriesWritten) {
        return new MlmVolumeUpdatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                sourceMemberId,
                businessVolume,
                currency,
                entriesWritten
        );
    }
}
