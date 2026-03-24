package com.ccapp.financing.dto;

import com.ccapp.identity.domain.enums.KycStatus;

import java.util.UUID;

public record AdminFinancingApplicationApplicantResponse(
        UUID accountHolderId,
        String firstName,
        String lastName,
        String fullName,
        String phoneNumber,
        String countryCode,
        String serviceCountryCode,
        KycStatus kycStatus
) {
}
