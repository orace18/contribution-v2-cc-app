package com.ccapp.wallet.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletPayResponse(
        UUID operationId,
        String status,
        BigDecimal amount,
        String currency,
        boolean otpRequired,
        UUID otpChallengeId,
        OffsetDateTime otpExpiresAt
) {
}
