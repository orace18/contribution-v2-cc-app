package com.ccapp.payments.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String countryCode,
        String grade,
        Integer durationMonths,
        java.math.BigDecimal unitAmount,
        String currency,
        String code,
        String name
) {
}
