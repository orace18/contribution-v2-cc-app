package com.ccapp.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AdminWalletMonitoringResponse(
        UUID walletId,
        UUID accountHolderId,
        String holderName,
        String phoneNumber,
        String walletPhoneNumber,
        String walletStatus,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        String currency,
        List<AdminWalletTransactionResponse> recentTransactions
) {
}
