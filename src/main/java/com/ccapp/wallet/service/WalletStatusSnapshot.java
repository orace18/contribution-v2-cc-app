package com.ccapp.wallet.service;

import com.ccapp.financial.access.domain.FinancialAccessReasonCode;

import java.util.List;
import java.util.UUID;

public record WalletStatusSnapshot(
        UUID walletId,
        boolean active,
        boolean hasPin,
        WalletState state,
        String message,
        boolean phoneVerified,
        boolean activationEligible,
        List<FinancialAccessReasonCode> blockingReasons
) {
    public WalletStatusSnapshot {
        blockingReasons = List.copyOf(blockingReasons == null ? List.of() : blockingReasons);
    }
}
