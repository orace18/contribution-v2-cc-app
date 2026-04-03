package com.ccapp.commerce.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromoCodeAppliedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID promoCodeId,
        String code,
        UUID buyerAccountHolderId,
        BigDecimal discountAmount
) implements DomainEvent {

    public static PromoCodeAppliedEvent create(UUID orderId,
                                               UUID promoCodeId,
                                               String code,
                                               UUID buyerAccountHolderId,
                                               BigDecimal discountAmount) {
        return new PromoCodeAppliedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                promoCodeId,
                code,
                buyerAccountHolderId,
                discountAmount
        );
    }
}
