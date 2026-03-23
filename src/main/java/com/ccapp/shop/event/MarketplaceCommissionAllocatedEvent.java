package com.ccapp.shop.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MarketplaceCommissionAllocatedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID buyerId,
        UUID merchantId,
        String countryCode,
        BigDecimal commissionAmount,
        String currency
) implements DomainEvent {

    public static MarketplaceCommissionAllocatedEvent create(UUID orderId,
                                                             UUID buyerId,
                                                             UUID merchantId,
                                                             String countryCode,
                                                             BigDecimal commissionAmount,
                                                             String currency) {
        return new MarketplaceCommissionAllocatedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                buyerId,
                merchantId,
                countryCode,
                commissionAmount,
                currency
        );
    }
}
