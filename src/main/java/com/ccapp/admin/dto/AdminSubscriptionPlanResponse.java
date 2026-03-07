package com.ccapp.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminSubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        String country,
        String currency,
        BigDecimal price,
        boolean active,
        OffsetDateTime createdAt
) {
}
