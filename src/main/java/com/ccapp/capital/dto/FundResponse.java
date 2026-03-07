package com.ccapp.capital.dto;

import com.ccapp.capital.domain.enums.FundProviderType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FundResponse(
        UUID id,
        String name,
        FundProviderType provider,
        BigDecimal totalCapital,
        BigDecimal availableCapital,
        String currency,
        OffsetDateTime createdAt
) {
}
