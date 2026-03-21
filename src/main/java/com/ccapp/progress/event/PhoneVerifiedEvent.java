package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PhoneVerifiedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID verificationId,
        String phoneNumber
) implements DomainEvent {

    public static PhoneVerifiedEvent create(UUID accountHolderId, UUID verificationId, String phoneNumber) {
        return new PhoneVerifiedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId, verificationId, phoneNumber);
    }
}
