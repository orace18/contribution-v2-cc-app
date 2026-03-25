package com.ccapp.mobile.onboarding.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectMobileCountryRequest(
        @NotBlank(message = "country is required")
        String country
) {
}
