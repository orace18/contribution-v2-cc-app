package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.BusinessExperienceLevel;
import com.ccapp.identity.domain.enums.OnboardingStep;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserProfileDto(
        String firstName,
        String lastName,
        String country,
        String phone,
        UUID profilePhotoMediaId,
        String activitySectorCode,
        BusinessExperienceLevel businessExperience,
        String monthlyIncomeBracketCode,
        String projectDescription,
        BigDecimal availableCapital,
        boolean solidarityGuaranteeAccepted,
        boolean termsAccepted,
        OffsetDateTime termsAcceptedAt,
        OnboardingStep onboardingStep,
        boolean onboardingCompleted,
        OffsetDateTime onboardingCompletedAt
) {
}
