package com.ccapp.admin.dto;

import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminSubscriptionPlanResponse(
        UUID id,
        String countryCode,
        SubscriptionGrade grade,
        Integer durationMonths,
        BigDecimal unitAmount,
        String currency,
        String code,
        String name,
        boolean active,
        OffsetDateTime createdAt
) {
}
