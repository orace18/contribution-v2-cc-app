package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.FinancialProductType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FinancingOfferResponse(
        UUID id,
        UUID partnerId,
        String partnerName,
        FinancialProductType type,
        String name,
        String description,
        String countryCode,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal interestRate,
        Integer durationMonths,
        String currency,
        String feesJson,
        String eligibilityRulesJson,
        String requiredDocumentsJson,
        String scoringRulesJson,
        FinancingOfferStatus status,
        boolean active,
        OffsetDateTime createdAt,
        List<OfferCountryConfigResponse> countryConfigs,
        List<OfferRequirementResponse> requirements
) {
}
