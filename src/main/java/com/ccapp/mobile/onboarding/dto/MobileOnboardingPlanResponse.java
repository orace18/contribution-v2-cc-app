package com.ccapp.mobile.onboarding.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MobileOnboardingPlanResponse(
        UUID id,
        String code,
        String name,
        Integer durationMonths,
        BigDecimal monthlyPrice,
        String currency,
        BigDecimal totalPrice,
        boolean isFree,
        List<String> features,
        boolean isPopular
) {
}
