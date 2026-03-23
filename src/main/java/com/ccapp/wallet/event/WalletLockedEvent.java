package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.payments.operation.domain.enums.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletLockedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID accountHolderId,
        OperationType operationType,
        String reason
) implements DomainEvent {

    public static WalletLockedEvent create(UUID walletId,
                                           UUID accountHolderId,
                                           OperationType operationType,
                                           String reason) {
        return new WalletLockedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                walletId,
                accountHolderId,
                operationType,
                reason
        );
    }
}
