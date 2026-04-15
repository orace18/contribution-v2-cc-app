package com.ccapp.kyc.dto;

import com.ccapp.kyc.domain.VerificationStatus;

import java.util.UUID;

public record UploadVerificationResponse(
        UUID verificationId,
        VerificationStatus status
) {
}
