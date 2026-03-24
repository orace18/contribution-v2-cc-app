package com.ccapp.mobile.onboarding.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectMobilePlanRequest(
        @NotNull(message = "planId is required")
        UUID planId
) {
}
