package com.ccapp.shop.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShopOrderPaidEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID buyerId,
        UUID merchantId,
        String countryCode,
        BigDecimal totalAmount,
        String currency,
        String paymentMethod
) implements DomainEvent {

    public static ShopOrderPaidEvent create(UUID orderId,
                                            UUID buyerId,
                                            UUID merchantId,
                                            String countryCode,
                                            BigDecimal totalAmount,
                                            String currency,
                                            String paymentMethod) {
        return new ShopOrderPaidEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                buyerId,
                merchantId,
                countryCode,
                totalAmount,
                currency,
                paymentMethod
        );
    }
}
