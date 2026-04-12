package com.ccapp.mobile.sponsor.dto;

import java.util.UUID;

public record SponsorLookupResponse(
        boolean found,
        Boolean eligible,
        UUID accountHolderId,
        String firstName,
        String lastName,
        String maskedPhone,
        String message,
        String reasonCode
) {

    public static SponsorLookupResponse notFound() {
        return new SponsorLookupResponse(false, null, null, null, null, null, "Sponsor not found", "NOT_FOUND");
    }

    public static SponsorLookupResponse notEligible(String message, String reasonCode) {
        return new SponsorLookupResponse(true, false, null, null, null, null, message, reasonCode);
    }

    public static SponsorLookupResponse eligible(UUID accountHolderId, String firstName, String lastName, String maskedPhone) {
        return new SponsorLookupResponse(true, true, accountHolderId, firstName, lastName, maskedPhone, null, null);
    }
}
