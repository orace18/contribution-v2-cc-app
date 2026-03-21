package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanApplicationSubmittedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID applicationId
) implements DomainEvent {

    public static LoanApplicationSubmittedEvent create(UUID accountHolderId, UUID applicationId) {
        return new LoanApplicationSubmittedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId, applicationId);
    }
}
