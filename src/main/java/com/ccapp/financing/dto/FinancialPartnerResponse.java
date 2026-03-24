package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.domain.enums.FinancialProductType;
import com.ccapp.financing.domain.enums.PartnerApiIntegrationType;

import java.time.OffsetDateTime;
import java.util.List;
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
        List<FinancialProductType> supportedProducts,
        PartnerApiIntegrationType apiIntegrationType,
        String settlementAccount,
        String apiEndpoint,
        OffsetDateTime createdAt
) {
}
