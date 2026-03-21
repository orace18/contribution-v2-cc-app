package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanDisbursedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID loanId
) implements DomainEvent {

    public static LoanDisbursedEvent create(UUID accountHolderId, UUID loanId) {
        return new LoanDisbursedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId, loanId);
    }
}
