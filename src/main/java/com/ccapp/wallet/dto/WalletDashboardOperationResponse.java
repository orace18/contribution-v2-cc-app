package com.ccapp.wallet.dto;

import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletDashboardOperationResponse(
        UUID operationId,
        WalletLedgerCategory type,
        BigDecimal amount,
        String currency,
        WalletLedgerStatus status,
        OffsetDateTime createdAt
) {
}
