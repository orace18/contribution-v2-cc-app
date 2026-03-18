package com.ccapp.country.config.dto;

import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CountryChartAccountSummaryResponse(
        UUID id,
        String countryCode,
        String accountCode,
        String accountName,
        CountryChartAccountType accountType,
        String currency,
        BigDecimal currentBalance,
        CountryChartAccountStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
