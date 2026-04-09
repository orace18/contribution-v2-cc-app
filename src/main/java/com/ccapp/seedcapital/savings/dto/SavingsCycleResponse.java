package com.ccapp.seedcapital.savings.dto;

import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SavingsCycleResponse(
        UUID id,
        UUID circleId,
        Integer cycleNumber,
        String countryCode,
        String currency,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        BigDecimal expectedAmountPerMember,
        int participantCount,
        int confirmedMembersCount,
        SavingsCycleStatus status,
        String note,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
