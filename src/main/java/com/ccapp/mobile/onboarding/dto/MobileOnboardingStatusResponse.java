package com.ccapp.mobile.onboarding.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MobileOnboardingStatusResponse(
        String currentStatus,
        List<String> completedSteps,
        NextStep nextStep,
        BigDecimal progressPercentage,
        boolean isActive,
        boolean isNetworkReady,
        UUID paymentOperationId,
        String paymentReference
) {
    public record NextStep(
            String code,
            String method,
            String endpoint,
            String description
    ) {
    }
}
