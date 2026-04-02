package com.ccapp.mobile.sponsor.dto;

import java.util.UUID;

public record SponsorLookupResponse(
        boolean found,
        Boolean eligible,
        UUID accountHolderId,
        String firstName,
        String lastName,
        String maskedPhone,
        String message
) {

    public static SponsorLookupResponse notFound() {
        return new SponsorLookupResponse(false, null, null, null, null, null, "Sponsor not found");
    }

    public static SponsorLookupResponse notEligible(String message) {
        return new SponsorLookupResponse(true, false, null, null, null, null, message);
    }

    public static SponsorLookupResponse eligible(UUID accountHolderId, String firstName, String lastName, String maskedPhone) {
        return new SponsorLookupResponse(true, true, accountHolderId, firstName, lastName, maskedPhone, null);
    }
}
