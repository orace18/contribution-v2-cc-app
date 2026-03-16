package com.ccapp.shop.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShopOrderRefundedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID buyerId,
        BigDecimal totalAmount,
        String currency,
        BigDecimal merchantAmount,
        BigDecimal platformCommission,
        BigDecimal mlmVolume
) implements DomainEvent {

    public static ShopOrderRefundedEvent create(UUID orderId,
                                                UUID buyerId,
                                                BigDecimal totalAmount,
                                                String currency,
                                                BigDecimal merchantAmount,
                                                BigDecimal platformCommission,
                                                BigDecimal mlmVolume) {
        return new ShopOrderRefundedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                buyerId,
                totalAmount,
                currency,
                merchantAmount,
                platformCommission,
                mlmVolume
        );
    }
}
