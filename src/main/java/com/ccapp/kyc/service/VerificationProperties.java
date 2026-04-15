package com.ccapp.kyc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VerificationProperties {

    private final int faceMatchThreshold;
    private final int autoApproveScoreThreshold;
    private final int reviewScoreThreshold;
    private final int weightDocumentReadability;
    private final int weightOcrConfidence;
    private final int weightDocumentValidity;
    private final int weightFaceMatch;
    private final int stubFaceMatchScore;

    public VerificationProperties(
            @Value("${app.kyc.verification.face-match-threshold:75}") int faceMatchThreshold,
            @Value("${app.kyc.verification.auto-approve-score-threshold:75}") int autoApproveScoreThreshold,
            @Value("${app.kyc.verification.review-score-threshold:50}") int reviewScoreThreshold,
            @Value("${app.kyc.verification.score-weight.document-readability:25}") int weightDocumentReadability,
            @Value("${app.kyc.verification.score-weight.ocr-confidence:20}") int weightOcrConfidence,
            @Value("${app.kyc.verification.score-weight.document-validity:20}") int weightDocumentValidity,
            @Value("${app.kyc.verification.score-weight.face-match:35}") int weightFaceMatch,
            @Value("${app.kyc.verification.stub.face-match-score:80}") int stubFaceMatchScore) {
        this.faceMatchThreshold = faceMatchThreshold;
        this.autoApproveScoreThreshold = autoApproveScoreThreshold;
        this.reviewScoreThreshold = reviewScoreThreshold;
        this.weightDocumentReadability = weightDocumentReadability;
        this.weightOcrConfidence = weightOcrConfidence;
        this.weightDocumentValidity = weightDocumentValidity;
        this.weightFaceMatch = weightFaceMatch;
        this.stubFaceMatchScore = stubFaceMatchScore;
    }

    public int faceMatchThreshold() { return faceMatchThreshold; }
    public int autoApproveScoreThreshold() { return autoApproveScoreThreshold; }
    public int reviewScoreThreshold() { return reviewScoreThreshold; }
    public int weightDocumentReadability() { return weightDocumentReadability; }
    public int weightOcrConfidence() { return weightOcrConfidence; }
    public int weightDocumentValidity() { return weightDocumentValidity; }
    public int weightFaceMatch() { return weightFaceMatch; }
    public int stubFaceMatchScore() { return stubFaceMatchScore; }
}
