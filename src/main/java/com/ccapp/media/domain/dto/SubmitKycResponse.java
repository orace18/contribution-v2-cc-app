package com.ccapp.media.domain.dto;

import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubmitKycResponse(
        UUID accountHolderId,
        KycStatus kycStatus,
        KycLevel kycLevel,
        OffsetDateTime submittedAt,
        String caseId,
        String productStatus,
        String reasonCode,
        String nextAction,
        String statusReason,
        String recommendation,
        Double confidenceScore
) {
}
