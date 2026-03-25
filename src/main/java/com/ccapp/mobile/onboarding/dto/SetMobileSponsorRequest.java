package com.ccapp.mobile.onboarding.dto;

import java.util.UUID;

public record SetMobileSponsorRequest(
        UUID sponsorAccountHolderId
) {
}
