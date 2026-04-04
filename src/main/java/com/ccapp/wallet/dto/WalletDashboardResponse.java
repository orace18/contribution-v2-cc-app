package com.ccapp.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletDashboardResponse(
        BigDecimal balance,
        BigDecimal monthlyIncome,
        BigDecimal totalWithdrawn,
        List<WalletDashboardOperationResponse> recentOperations
) {
}
