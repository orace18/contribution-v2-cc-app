package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletReservedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID accountHolderId,
        BigDecimal amount,
        UUID reservationId
) implements DomainEvent {

    public static WalletReservedEvent create(UUID walletId, UUID accountHolderId,
                                             BigDecimal amount, UUID reservationId) {
        return new WalletReservedEvent(
                UUID.randomUUID(), OffsetDateTime.now(),
                walletId, accountHolderId, amount, reservationId);
    }
}
