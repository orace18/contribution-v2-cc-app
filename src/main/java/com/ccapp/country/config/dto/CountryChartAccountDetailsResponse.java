package com.ccapp.country.config.dto;

import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CountryChartAccountDetailsResponse(
        UUID id,
        String countryCode,
        String accountCode,
        String accountNumber,
        String accountName,
        String label,
        CountryAccountGroup groupId,
        CountryChartAccountType accountType,
        String currency,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        CountryChartAccountStatus status,
        UUID accountHolderId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
