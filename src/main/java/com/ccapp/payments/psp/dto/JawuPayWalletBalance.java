package com.ccapp.payments.psp.dto;

import java.math.BigDecimal;

public record JawuPayWalletBalance(
        String walletId,
        BigDecimal balance,
        String currency
) {
}
