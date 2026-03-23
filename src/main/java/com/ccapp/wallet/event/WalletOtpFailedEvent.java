package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.payments.operation.domain.enums.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletOtpFailedEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID accountHolderId,
        UUID operationId,
        UUID challengeId,
        OperationType operationType,
        String reason,
        int remainingAttempts
) implements DomainEvent {

    public static WalletOtpFailedEvent create(UUID walletId,
                                              UUID accountHolderId,
                                              UUID operationId,
                                              UUID challengeId,
                                              OperationType operationType,
                                              String reason,
                                              int remainingAttempts) {
        return new WalletOtpFailedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                walletId,
                accountHolderId,
                operationId,
                challengeId,
                operationType,
                reason,
                remainingAttempts
        );
    }
}
