package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.FinancialProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateFinancingOfferRequest(
        @NotNull FinancialProductType type,
        @NotBlank String name,
        String description,
        @NotBlank @Size(min = 2, max = 3) String countryCode,
        @NotNull @DecimalMin("0.0001") BigDecimal minAmount,
        @NotNull @DecimalMin("0.0001") BigDecimal maxAmount,
        @NotNull @DecimalMin("0.00") BigDecimal interestRate,
        @NotNull @Positive Integer durationMonths,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank String feesJson,
        @NotBlank String eligibilityRulesJson,
        @NotBlank String requiredDocumentsJson,
        @NotBlank String scoringRulesJson,
        @NotNull FinancingOfferStatus status,
        @Valid List<OfferRequirementUpsertRequest> requirements
) {
}
