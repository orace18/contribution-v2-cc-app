package com.ccapp.financing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateLoanApplicationRequest(
        @NotNull UUID offerId,
        UUID circleId,
        @NotNull @DecimalMin("0.0001") BigDecimal requestedAmount
) {
}
