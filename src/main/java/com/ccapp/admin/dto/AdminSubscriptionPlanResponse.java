package com.ccapp.admin.dto;

import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminSubscriptionPlanResponse(
        UUID id,
        String countryCode,
        SubscriptionGrade formulaType,
        Integer durationMonths,
        BigDecimal monthlyPrice,
        BigDecimal totalPrice,
        String currency,
        boolean isActive,
        OffsetDateTime createdAt
) {
}
