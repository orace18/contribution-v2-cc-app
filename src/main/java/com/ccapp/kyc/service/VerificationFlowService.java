package com.ccapp.kyc.service;

import com.ccapp.kyc.domain.IdentityVerificationEntity;
import com.ccapp.kyc.domain.VerificationAnalysisEntity;
import com.ccapp.kyc.domain.VerificationDocumentEntity;
import com.ccapp.kyc.domain.VerificationStatus;
import com.ccapp.kyc.domain.VerificationStep;
import com.ccapp.kyc.dto.VerificationResponse.ScoreDetailsDto;
import com.ccapp.kyc.repository.IdentityVerificationRepository;
import com.ccapp.kyc.repository.VerificationAnalysisRepository;
import com.ccapp.kyc.repository.VerificationDocumentRepository;
import com.ccapp.media.service.KycReviewService;
import com.ccapp.media.storage.ObjectStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Asynchronous processing pipeline for identity verification.
 *
 * <p>Triggered after the upload transaction commits via {@link VerificationProcessingEvent}.
 * Pipeline: OCR → expiry check → face match → scoring → decision → persist.
 *
 * <p>No client-provided values are consumed here. All analysis runs server-side on raw bytes.
 */
@Service
public class VerificationFlowService {

    private static final Logger log = LoggerFactory.getLogger(VerificationFlowService.class);

    private final IdentityVerificationRepository identityVerificationRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final VerificationAnalysisRepository verificationAnalysisRepository;
    private final OcrService ocrService;
    private final FaceMatchService faceMatchService;
    private final VerificationScoringService scoringService;
    private final ObjectStorageService objectStorageService;
    private final KycReviewService kycReviewService;
    private final ObjectMapper objectMapper;

    public VerificationFlowService(IdentityVerificationRepository identityVerificationRepository,
                                   VerificationDocumentRepository verificationDocumentRepository,
                                   VerificationAnalysisRepository verificationAnalysisRepository,
                                   OcrService ocrService,
                                   FaceMatchService faceMatchService,
                                   VerificationScoringService scoringService,
                                   ObjectStorageService objectStorageService,
                                   KycReviewService kycReviewService,
                                   ObjectMapper objectMapper) {
        this.identityVerificationRepository = identityVerificationRepository;
        this.verificationDocumentRepository = verificationDocumentRepository;
        this.verificationAnalysisRepository = verificationAnalysisRepository;
        this.ocrService = ocrService;
        this.faceMatchService = faceMatchService;
        this.scoringService = scoringService;
        this.objectStorageService = objectStorageService;
        this.kycReviewService = kycReviewService;
        this.objectMapper = objectMapper;
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationSubmitted(VerificationProcessingEvent event) {
        process(event.verificationId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID verificationId) {
        Optional<IdentityVerificationEntity> optVerification = identityVerificationRepository.findById(verificationId);
        if (optVerification.isEmpty()) {
            log.warn("verification flow skipped — not found verificationId={}", verificationId);
            return;
        }

        IdentityVerificationEntity verification = optVerification.get();

        if (verification.getStatus() != VerificationStatus.PROCESSING) {
            log.info("verification flow skipped — status={} verificationId={}", verification.getStatus(), verificationId);
            return;
        }

        Optional<VerificationDocumentEntity> optDoc = verificationDocumentRepository.findByVerification(verification);
        if (optDoc.isEmpty()) {
            log.warn("verification flow skipped — no documents found verificationId={}", verificationId);
            return;
        }

        VerificationDocumentEntity doc = optDoc.get();
        log.info("verification flow start verificationId={} documentType={}", verificationId, verification.getDocumentType());

        // Step 1: OCR on document front
        verification.setCurrentStep(VerificationStep.DOCUMENT_ANALYSIS);
        identityVerificationRepository.save(verification);

        byte[] documentFrontBytes = readFromStorage(doc.getBucketName(), doc.getDocumentFrontKey());
        OcrService.OcrResult ocrResult = ocrService.extract(documentFrontBytes, resolveContentType(doc.getDocumentFrontKey()));

        log.info("verification flow ocr done verificationId={} readable={} confidence={} expiryDate={}",
                verificationId, ocrResult.readable(), ocrResult.confidence(), ocrResult.expiryDate());

        boolean documentExpired = isExpired(ocrResult.expiryDate());

        // Step 2: Face match
        verification.setCurrentStep(VerificationStep.FACE_MATCH);
        identityVerificationRepository.save(verification);

        byte[] selfieBytes = readFromStorage(doc.getBucketName(), doc.getSelfieKey());
        byte[] portraitBytes = ocrResult.portraitBytes();

        FaceMatchService.FaceMatchResult faceMatchResult;
        boolean faceMatchAvailable;

        if (portraitBytes != null && portraitBytes.length > 0) {
            faceMatchResult = faceMatchService.compare(portraitBytes, selfieBytes);
            faceMatchAvailable = true;
        } else {
            faceMatchResult = faceMatchService.compare(documentFrontBytes, selfieBytes);
            faceMatchAvailable = faceMatchResult.faceDetectedInFirst() && faceMatchResult.faceDetectedInSecond();
            log.info("verification flow face match fallback verificationId={} available={}", verificationId, faceMatchAvailable);
        }

        log.info("verification flow face match done verificationId={} matched={} score={} available={}",
                verificationId, faceMatchResult.matched(), faceMatchResult.score(), faceMatchAvailable);

        // Step 3: Scoring and decision
        VerificationScoringService.ScoringResult scoring = scoringService.score(
                ocrResult.readable(),
                ocrResult.confidence(),
                documentExpired,
                faceMatchResult.score(),
                faceMatchAvailable
        );

        log.info("verification flow scoring done verificationId={} score={} status={} reason={}",
                verificationId, scoring.reliabilityScore(), scoring.status(), scoring.decisionReason());

        // Step 4: Persist analysis
        VerificationAnalysisEntity analysis = verificationAnalysisRepository
                .findByVerification(verification)
                .orElseGet(VerificationAnalysisEntity::new);

        analysis.setVerification(verification);
        analysis.setDocumentReadable(ocrResult.readable());
        analysis.setOcrConfidence(ocrResult.confidence());
        analysis.setExtractedFullName(ocrResult.fullName());
        analysis.setExtractedDocumentNumber(ocrResult.documentNumber());
        analysis.setExtractedBirthDate(ocrResult.dateOfBirth());
        analysis.setExtractedExpiryDate(ocrResult.expiryDate());
        analysis.setExtractedNationality(ocrResult.nationality());
        analysis.setDocumentExpired(documentExpired);
        analysis.setFaceMatchScore(faceMatchResult.score());
        analysis.setFaceMatched(faceMatchResult.matched());
        analysis.setScoreDetailsJson(serializeScoreDetails(scoring.scoreDetails()));
        verificationAnalysisRepository.save(analysis);

        // Step 5: Update verification
        verification.setCurrentStep(VerificationStep.COMPLETED);
        verification.setStatus(scoring.status());
        verification.setReliabilityScore(scoring.reliabilityScore());
        verification.setDecisionReason(scoring.decisionReason());
        identityVerificationRepository.save(verification);

        // Step 6: Propagate result to account holder KYC status
        UUID accountHolderId = verification.getAccountHolderId();
        switch (scoring.status()) {
            case APPROVED -> kycReviewService.approveKyc(accountHolderId);
            case REJECTED -> kycReviewService.rejectKyc(accountHolderId);
            case REVIEW   -> kycReviewService.markPending(accountHolderId);
            default       -> log.warn("verification flow unexpected final status={} verificationId={}", scoring.status(), verificationId);
        }

        log.info("verification flow completed verificationId={} finalStatus={} score={}",
                verificationId, scoring.status(), scoring.reliabilityScore());
    }

    private byte[] readFromStorage(String bucketName, String objectKey) {
        try {
            byte[] data = objectStorageService.readObject(bucketName, objectKey);
            log.info("verification flow storage read success bucket={} key={} bytes={}", bucketName, objectKey, data.length);
            return data;
        } catch (Exception ex) {
            log.error("verification flow storage read failed bucket={} key={} error={}", bucketName, objectKey, ex.getMessage());
            return new byte[0];
        }
    }

    private boolean isExpired(LocalDate expiryDate) {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    private String resolveContentType(String objectKey) {
        if (objectKey == null) return "application/octet-stream";
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private String serializeScoreDetails(ScoreDetailsDto details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            log.warn("verification flow failed to serialize score details: {}", ex.getMessage());
            return null;
        }
    }
}
