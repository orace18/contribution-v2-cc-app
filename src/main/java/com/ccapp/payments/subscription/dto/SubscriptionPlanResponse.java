package com.ccapp.payments.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        String country,
        String currency,
        BigDecimal price
) {
}
