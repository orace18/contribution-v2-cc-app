package com.ccapp.commerce.event;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DsbDistributedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID buyerAccountHolderId,
        CommerceDistributionMode distributionMode,
        BigDecimal totalDsbAmount,
        String currency,
        List<Beneficiary> beneficiaries
) implements DomainEvent {

    public static DsbDistributedEvent create(UUID orderId,
                                             UUID buyerAccountHolderId,
                                             CommerceDistributionMode distributionMode,
                                             BigDecimal totalDsbAmount,
                                             String currency,
                                             List<Beneficiary> beneficiaries) {
        return new DsbDistributedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                buyerAccountHolderId,
                distributionMode,
                totalDsbAmount,
                currency,
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
