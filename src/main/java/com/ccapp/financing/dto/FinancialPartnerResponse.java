package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FinancialPartnerResponse(
        UUID id,
        String name,
        FinancialPartnerType type,
        String countryCode,
        String currency,
        String contactEmail,
        String description,
        FinancialPartnerStatus status,
        String apiEndpoint,
        OffsetDateTime createdAt
) {
}
