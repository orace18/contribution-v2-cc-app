package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;

public record KycStatusResponse(
        KycStatus kycStatus,
        KycLevel kycLevel,
        String caseId,
        String productStatus,
        String reasonCode,
        String nextAction,
        String statusReason,
        String recommendation,
        Double confidenceScore
) {
}
