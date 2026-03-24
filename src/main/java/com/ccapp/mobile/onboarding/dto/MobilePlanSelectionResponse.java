package com.ccapp.mobile.onboarding.dto;

import java.util.UUID;

public record MobilePlanSelectionResponse(
        UUID selectionId,
        UUID planId,
        String planCode,
        String planName,
        String status,
        NextStep nextStep
) {
    public record NextStep(
            String type,
            String title,
            String description,
            String ctaLabel,
            String ctaAction
    ) {
    }
}
