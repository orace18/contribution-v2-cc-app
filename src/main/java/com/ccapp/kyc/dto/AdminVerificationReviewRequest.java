package com.ccapp.kyc.dto;

import com.ccapp.kyc.domain.VerificationStatus;
import jakarta.validation.constraints.NotNull;

public record AdminVerificationReviewRequest(
        @NotNull(message = "status is required") VerificationStatus status,
        String reason
) {
}
