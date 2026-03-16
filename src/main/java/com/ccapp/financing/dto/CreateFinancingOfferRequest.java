package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateFinancingOfferRequest(
        @NotNull UUID partnerId,
        @NotBlank String name,
        String description,
        @NotBlank String loanType,
        @NotNull @DecimalMin("0.0001") BigDecimal minAmount,
        @NotNull @DecimalMin("0.0001") BigDecimal maxAmount,
        @NotNull @DecimalMin("0.00") BigDecimal interestRate,
        @NotNull @Positive Integer durationMonths,
        @NotBlank @Size(min = 3, max = 3) String currency,
        FinancingOfferStatus status,
        @Valid List<OfferRequirementUpsertRequest> requirements
) {
}
