package com.ccapp.mobile.onboarding.dto;

import java.util.UUID;

public record ActivateFreePlanResponse(
        UUID operationId,
        String membershipStatus,
        String journeyStatus,
        boolean active
) {
}
