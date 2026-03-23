package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletReleasedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID reservationId,
        BigDecimal releasedAmount
) implements DomainEvent {

    public static WalletReleasedEvent create(UUID walletId, UUID reservationId, BigDecimal releasedAmount) {
        return new WalletReleasedEvent(
                UUID.randomUUID(), OffsetDateTime.now(),
                walletId, reservationId, releasedAmount);
    }
}
