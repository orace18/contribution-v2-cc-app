package com.ccapp.progress.event;

import com.ccapp.common.events.DomainEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepaymentCompletedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID accountHolderId,
        UUID loanId,
        UUID repaymentId
) implements DomainEvent {

    public static RepaymentCompletedEvent create(UUID accountHolderId, UUID loanId, UUID repaymentId) {
        return new RepaymentCompletedEvent(UUID.randomUUID(), OffsetDateTime.now(), accountHolderId, loanId, repaymentId);
    }
}
