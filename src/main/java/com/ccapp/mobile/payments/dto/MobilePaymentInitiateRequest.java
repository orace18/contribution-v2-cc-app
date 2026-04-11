package com.ccapp.mobile.payments.dto;

import com.ccapp.common.security.SensitiveDataRedactor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MobilePaymentInitiateRequest(
        @Size(max = 128)
        String idempotencyKey,

        @NotBlank
        @Size(min = 2, max = 3)
        String country,

        @NotBlank
        @Size(max = 64)
        String paymentMethodId,

        @Size(max = 32)
        String msisdn,

        @Size(max = 32)
        String operatorHint,

        @Size(max = 64)
        String paymentType,

        UUID planId,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin
) {
    @Override
    public String toString() {
        return "MobilePaymentInitiateRequest[" +
                "idempotencyKey=" + idempotencyKey +
                ", country=" + country +
                ", paymentMethodId=" + paymentMethodId +
                ", msisdn=" + msisdn +
                ", operatorHint=" + operatorHint +
                ", paymentType=" + paymentType +
                ", planId=" + planId +
                ", pin=" + SensitiveDataRedactor.REDACTED +
                "]";
    }
}
