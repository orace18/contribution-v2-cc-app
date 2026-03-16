package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancingOfferStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FinancingOfferResponse(
        UUID id,
        UUID partnerId,
        String name,
        String description,
        String loanType,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal interestRate,
        Integer durationMonths,
        String currency,
        FinancingOfferStatus status,
        OffsetDateTime createdAt,
        List<OfferCountryConfigResponse> countryConfigs,
        List<OfferRequirementResponse> requirements
) {
}
