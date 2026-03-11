package com.ccapp.payments.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommissionGeneratedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID operationId,
        UUID beneficiaryAccountHolderId,
        BigDecimal amount,
        String currency,
        String source
) implements DomainEvent {

    public static CommissionGeneratedEvent create(UUID operationId,
                                                  UUID beneficiaryAccountHolderId,
                                                  BigDecimal amount,
                                                  String currency,
                                                  String source) {
        return new CommissionGeneratedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                operationId,
                beneficiaryAccountHolderId,
                amount,
                currency,
                source
        );
    }
}
