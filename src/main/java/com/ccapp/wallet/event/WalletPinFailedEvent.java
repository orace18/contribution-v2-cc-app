package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.payments.operation.domain.enums.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletPinFailedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID accountHolderId,
        OperationType operationType,
        int remainingAttempts
) implements DomainEvent {

    public static WalletPinFailedEvent create(UUID walletId,
                                              UUID accountHolderId,
                                              OperationType operationType,
                                              int remainingAttempts) {
        return new WalletPinFailedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                walletId,
                accountHolderId,
                operationType,
                remainingAttempts
        );
    }
}
