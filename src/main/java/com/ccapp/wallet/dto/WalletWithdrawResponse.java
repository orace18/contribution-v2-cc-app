package com.ccapp.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletWithdrawResponse(
        UUID operationId,
        String status,
        BigDecimal amount,
        String currency
) {
}
