package com.ccapp.kyc.dto;

import com.ccapp.kyc.domain.VerificationStatus;
import com.ccapp.kyc.domain.VerificationStep;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VerificationResponse(
        UUID verificationId,
        VerificationStatus status,
        VerificationStep currentStep,
        DocumentAnalysisDto documentAnalysis,
        FaceMatchDto faceMatch,
        Integer reliabilityScore,
        ScoreDetailsDto scoreDetails,
        String decisionReason,
        String documentFrontUrl,
        String documentBackUrl,
        String selfieUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public record DocumentAnalysisDto(
            Boolean documentReadable,
            Boolean documentExpired,
            String documentType,
            String documentNumber,
            String fullName,
            String dateOfBirth,
            String expiryDate
    ) {
    }

    public record FaceMatchDto(
            Boolean matched,
            Integer score
    ) {
    }

    public record ScoreDetailsDto(
            Integer documentReadability,
            Integer ocrConfidence,
            Integer documentValidity,
            Integer faceMatch
    ) {
    }
}
