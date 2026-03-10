package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.BusinessExperienceLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OnboardingStep2Request(
        @NotBlank(message = "activitySectorCode is required")
        @Size(max = 64, message = "activitySectorCode max size is 64")
        String activitySectorCode,

        @NotNull(message = "businessExperience is required")
        BusinessExperienceLevel businessExperience,

        @NotBlank(message = "monthlyIncomeBracketCode is required")
        @Size(max = 64, message = "monthlyIncomeBracketCode max size is 64")
        String monthlyIncomeBracketCode,

        @NotBlank(message = "projectDescription is required")
        @Size(min = 20, max = 1000, message = "projectDescription size must be between 20 and 1000")
        String projectDescription
) {
}
