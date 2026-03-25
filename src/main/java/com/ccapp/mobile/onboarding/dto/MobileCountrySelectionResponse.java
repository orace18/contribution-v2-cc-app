package com.ccapp.mobile.onboarding.dto;

import java.util.UUID;

public record MobileCountrySelectionResponse(
        UUID accountHolderId,
        String countryCode
) {
}
