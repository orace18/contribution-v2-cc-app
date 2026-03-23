package com.ccapp.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletDepositResponse(
        UUID operationId,
        String status,
        BigDecimal amount,
        String currency
) {
}
