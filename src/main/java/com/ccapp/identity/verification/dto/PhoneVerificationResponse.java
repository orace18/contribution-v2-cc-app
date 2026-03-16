package com.ccapp.identity.verification.dto;

import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PhoneVerificationResponse(
        UUID id,
        String phoneNumberMasked,
        PhoneVerificationChannel channel,
        PhoneVerificationStatus status,
        OffsetDateTime resendAvailableAt,
        OffsetDateTime expiresAt,
        OffsetDateTime verifiedAt
) {
}
