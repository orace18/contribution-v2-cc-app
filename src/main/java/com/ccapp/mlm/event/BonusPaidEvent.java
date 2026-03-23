package com.ccapp.mlm.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BonusPaidEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID beneficiaryAccountHolderId,
        UUID payoutOperationId,
        BigDecimal grossAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        String currency
) implements DomainEvent {

    public static BonusPaidEvent create(UUID beneficiaryAccountHolderId,
                                        UUID payoutOperationId,
                                        BigDecimal grossAmount,
                                        BigDecimal taxAmount,
                                        BigDecimal netAmount,
                                        String currency) {
        return new BonusPaidEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                beneficiaryAccountHolderId,
                payoutOperationId,
                grossAmount,
                taxAmount,
                netAmount,
                currency
        );
    }
}
