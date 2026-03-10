package com.ccapp.identity.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OnboardingStep3Request(
        @NotNull(message = "availableCapital is required")
        @DecimalMin(value = "0.00", message = "availableCapital must be >= 0")
        BigDecimal availableCapital,

        @NotNull(message = "solidarityGuaranteeAccepted is required")
        Boolean solidarityGuaranteeAccepted,

        @NotNull(message = "termsAccepted is required")
        Boolean termsAccepted
) {
}
