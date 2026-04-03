package com.ccapp.commerce.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DsbReversedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        BigDecimal reversedAmount,
        List<Beneficiary> beneficiaries
) implements DomainEvent {

    public static DsbReversedEvent create(UUID orderId,
                                          BigDecimal reversedAmount,
                                          List<Beneficiary> beneficiaries) {
        return new DsbReversedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                reversedAmount,
                beneficiaries == null ? List.of() : List.copyOf(beneficiaries)
        );
    }

    public record Beneficiary(
            UUID accountHolderId,
            String label,
            BigDecimal amount
    ) {
    }
}
