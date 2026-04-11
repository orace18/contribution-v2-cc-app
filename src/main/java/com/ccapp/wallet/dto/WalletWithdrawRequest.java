package com.ccapp.wallet.dto;

import com.ccapp.common.security.SensitiveDataRedactor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletWithdrawRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3) String currency,

        @NotBlank @Size(max = 128) String idempotencyKey,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin,

        UUID otpChallengeId,

        @Pattern(regexp = "\\d{4,8}", message = "OTP code must be 4 to 8 digits")
        String otpCode
) {
    @Override
    public String toString() {
        return "WalletWithdrawRequest[" +
                "amount=" + amount +
                ", currency=" + currency +
                ", idempotencyKey=" + idempotencyKey +
                ", pin=" + SensitiveDataRedactor.REDACTED +
                ", otpChallengeId=" + otpChallengeId +
                ", otpCode=" + SensitiveDataRedactor.REDACTED +
                "]";
    }
}
