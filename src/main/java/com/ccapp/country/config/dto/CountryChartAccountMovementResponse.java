package com.ccapp.country.config.dto;

import com.ccapp.ledger.domain.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CountryChartAccountMovementResponse(
        UUID id,
        UUID eventId,
        Integer lineNo,
        LedgerEntryType entryType,
        BigDecimal amount,
        String currency,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
