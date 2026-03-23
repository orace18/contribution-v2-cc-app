package com.ccapp.wallet.event;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.payments.operation.domain.enums.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletOtpSentEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID walletId,
        UUID accountHolderId,
        UUID operationId,
        UUID challengeId,
        OperationType operationType,
        OffsetDateTime expiresAt
) implements DomainEvent {

    public static WalletOtpSentEvent create(UUID walletId,
                                            UUID accountHolderId,
                                            UUID operationId,
                                            UUID challengeId,
                                            OperationType operationType,
                                            OffsetDateTime expiresAt) {
        return new WalletOtpSentEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                walletId,
                accountHolderId,
                operationId,
                challengeId,
                operationType,
                expiresAt
        );
    }
}
