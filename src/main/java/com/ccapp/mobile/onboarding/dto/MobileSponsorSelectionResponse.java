package com.ccapp.mobile.onboarding.dto;

import java.util.UUID;

public record MobileSponsorSelectionResponse(
        UUID accountHolderId,
        UUID sponsorAccountHolderId,
        boolean sponsorSet,
        String sponsorDecision
) {
}
