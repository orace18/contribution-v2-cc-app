package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.OnboardingStep;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OnboardingStatusResponse(
        UUID userId,
        OnboardingStep onboardingStep,
        int currentStep,
        int totalSteps,
        BigDecimal progressPercentage,
        boolean step1Completed,
        boolean step2Completed,
        boolean step3Completed,
        boolean onboardingCompleted,
        OffsetDateTime onboardingCompletedAt,
        UserProfileDto profile
) {
}
