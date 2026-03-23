package com.ccapp.wallet.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletReserveResponse(
        UUID reservationId,
        UUID walletId,
        BigDecimal amount,
        String status,
        OffsetDateTime expiryDate
) {
}
