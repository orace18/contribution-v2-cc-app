package com.ccapp.credit.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanRepaidEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID loanId,
        UUID accountHolderId,
        UUID actorUserId,
        UUID circleId,
        BigDecimal amount,
        String currency
) implements DomainEvent {

    public static LoanRepaidEvent create(UUID loanId,
                                         UUID accountHolderId,
                                         UUID actorUserId,
                                         UUID circleId,
                                         BigDecimal amount,
                                         String currency) {
        return new LoanRepaidEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                loanId,
                accountHolderId,
                actorUserId,
                circleId,
                amount,
                currency
        );
    }
}
