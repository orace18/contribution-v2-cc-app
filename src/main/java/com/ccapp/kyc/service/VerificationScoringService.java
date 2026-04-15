package com.ccapp.kyc.service;

import com.ccapp.kyc.domain.VerificationStatus;
import com.ccapp.kyc.dto.VerificationResponse.ScoreDetailsDto;
import org.springframework.stereotype.Service;

/**
 * Computes the reliability score and determines the final verification status.
 *
 * <p>Weighted score formula (default weights sum to 100):
 * <ul>
 *   <li>documentReadability: 25
 *   <li>ocrConfidence:       20
 *   <li>documentValidity:    20
 *   <li>faceMatch:           35
 * </ul>
 *
 * <p>Decision thresholds (configurable via {@link VerificationProperties}):
 * <ul>
 *   <li>total >= 75 → APPROVED
 *   <li>total >= 50 → REVIEW
 *   <li>total < 50  → REJECTED
 * </ul>
 */
@Service
public class VerificationScoringService {

    private final VerificationProperties properties;

    public VerificationScoringService(VerificationProperties properties) {
        this.properties = properties;
    }

    public ScoringResult score(boolean documentReadable,
                               int ocrConfidence,
                               boolean documentExpired,
                               int faceMatchScore,
                               boolean faceMatchAvailable) {

        int readabilityRaw = documentReadable ? 100 : (ocrConfidence < 30 ? 10 : 40);
        int ocrRaw = Math.min(100, Math.max(0, ocrConfidence));
        int validityRaw = (!documentExpired && documentReadable) ? 100 : (documentExpired ? 0 : 50);
        int faceRaw = faceMatchAvailable ? Math.min(100, Math.max(0, faceMatchScore)) : 0;

        int readabilityScore = weighted(readabilityRaw, properties.weightDocumentReadability());
        int ocrScore = weighted(ocrRaw, properties.weightOcrConfidence());
        int validityScore = weighted(validityRaw, properties.weightDocumentValidity());
        int faceScore = weighted(faceRaw, properties.weightFaceMatch());

        int total = readabilityScore + ocrScore + validityScore + faceScore;
        ScoreDetailsDto details = new ScoreDetailsDto(readabilityScore, ocrScore, validityScore, faceScore);

        String reason;
        VerificationStatus status;

        if (!documentReadable) {
            status = ocrConfidence < 30 ? VerificationStatus.REJECTED : VerificationStatus.REVIEW;
            reason = "DOCUMENT_UNREADABLE";
        } else if (documentExpired) {
            status = VerificationStatus.REJECTED;
            reason = "DOCUMENT_EXPIRED";
        } else if (faceMatchAvailable && faceMatchScore < properties.faceMatchThreshold()) {
            status = VerificationStatus.REVIEW;
            reason = "FACE_MISMATCH";
        } else if (total >= properties.autoApproveScoreThreshold()) {
            status = VerificationStatus.APPROVED;
            reason = "FACE_MATCH_OK_AND_DOCUMENT_VALID";
        } else if (total >= properties.reviewScoreThreshold()) {
            status = VerificationStatus.REVIEW;
            reason = "LOW_CONFIDENCE_SCORE";
        } else {
            status = VerificationStatus.REJECTED;
            reason = "INSUFFICIENT_RELIABILITY_SCORE";
        }

        return new ScoringResult(total, status, reason, details);
    }

    private int weighted(int raw, int weight) {
        return (int) Math.round(raw * weight / 100.0);
    }

    public record ScoringResult(
            int reliabilityScore,
            VerificationStatus status,
            String decisionReason,
            ScoreDetailsDto scoreDetails
    ) {
    }
}
