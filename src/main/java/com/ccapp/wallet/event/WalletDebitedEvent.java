package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletDebitedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID accountHolderId,
        BigDecimal amount,
        String currency,
        UUID operationId
) implements DomainEvent {

    public static WalletDebitedEvent create(UUID walletId, UUID accountHolderId,
                                            BigDecimal amount, String currency, UUID operationId) {
        return new WalletDebitedEvent(
                UUID.randomUUID(), OffsetDateTime.now(),
                walletId, accountHolderId, amount, currency, operationId);
    }
}
