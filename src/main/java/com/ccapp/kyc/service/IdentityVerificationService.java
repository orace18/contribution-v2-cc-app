package com.ccapp.kyc.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.media.service.KycReviewService;
import com.ccapp.kyc.domain.IdentityVerificationEntity;
import com.ccapp.kyc.domain.VerificationAnalysisEntity;
import com.ccapp.kyc.domain.VerificationDocumentEntity;
import com.ccapp.kyc.domain.VerificationStatus;
import com.ccapp.kyc.domain.VerificationStep;
import com.ccapp.kyc.dto.AdminVerificationReviewRequest;
import com.ccapp.kyc.dto.InitVerificationRequest;
import com.ccapp.kyc.dto.InitVerificationResponse;
import com.ccapp.kyc.dto.UploadVerificationResponse;
import com.ccapp.kyc.dto.VerificationResponse;
import com.ccapp.kyc.dto.VerificationResponse.DocumentAnalysisDto;
import com.ccapp.kyc.dto.VerificationResponse.FaceMatchDto;
import com.ccapp.kyc.dto.VerificationResponse.ScoreDetailsDto;
import com.ccapp.kyc.repository.IdentityVerificationRepository;
import com.ccapp.kyc.repository.VerificationAnalysisRepository;
import com.ccapp.kyc.repository.VerificationDocumentRepository;
import com.ccapp.media.storage.ObjectStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationService.class);

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            MediaType.IMAGE_JPEG_VALUE, "image/jpg", MediaType.IMAGE_PNG_VALUE, "image/webp");
    private static final List<String> ALLOWED_DOCUMENT_TYPES = List.of(
            MediaType.IMAGE_JPEG_VALUE, "image/jpg", MediaType.IMAGE_PNG_VALUE, "image/webp",
            MediaType.APPLICATION_PDF_VALUE);

    private final IdentityVerificationRepository identityVerificationRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final VerificationAnalysisRepository verificationAnalysisRepository;
    private final ObjectStorageService objectStorageService;
    private final KycReviewService kycReviewService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String kycBucket;
    private final long downloadUrlTtlMinutes;

    public IdentityVerificationService(IdentityVerificationRepository identityVerificationRepository,
                                       VerificationDocumentRepository verificationDocumentRepository,
                                       VerificationAnalysisRepository verificationAnalysisRepository,
                                       ObjectStorageService objectStorageService,
                                       KycReviewService kycReviewService,
                                       ApplicationEventPublisher eventPublisher,
                                       ObjectMapper objectMapper,
                                       @Value("${media.bucket.kyc:cc-media-kyc}") String kycBucket,
                                       @Value("${media.download-url-ttl-minutes:15}") long downloadUrlTtlMinutes) {
        this.identityVerificationRepository = identityVerificationRepository;
        this.verificationDocumentRepository = verificationDocumentRepository;
        this.verificationAnalysisRepository = verificationAnalysisRepository;
        this.objectStorageService = objectStorageService;
        this.kycReviewService = kycReviewService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.kycBucket = kycBucket;
        this.downloadUrlTtlMinutes = downloadUrlTtlMinutes;
    }

    @Transactional
    public InitVerificationResponse init(UUID accountHolderId, InitVerificationRequest request) {
        IdentityVerificationEntity verification = new IdentityVerificationEntity();
        verification.setAccountHolderId(accountHolderId);
        verification.setReferenceId(blankToNull(request.referenceId()));
        verification.setDocumentType(request.documentType());
        verification.setFirstName(blankToNull(request.firstName()));
        verification.setLastName(blankToNull(request.lastName()));
        verification.setPhoneNumber(blankToNull(request.phoneNumber()));
        verification.setStatus(VerificationStatus.INITIATED);
        identityVerificationRepository.save(verification);

        log.info("verification init accountHolderId={} verificationId={} documentType={}",
                accountHolderId, verification.getId(), request.documentType());

        return new InitVerificationResponse(verification.getId(), verification.getStatus());
    }

    @Transactional
    public UploadVerificationResponse upload(UUID accountHolderId,
                                             UUID verificationId,
                                             MultipartFile documentFront,
                                             MultipartFile documentBack,
                                             MultipartFile selfie,
                                             String captureDevice,
                                             String appVersion,
                                             String platform) {
        IdentityVerificationEntity verification = requireAccountHolderVerification(accountHolderId, verificationId);

        if (verification.getStatus() != VerificationStatus.INITIATED) {
            throw new BusinessException("Files have already been uploaded for this verification");
        }

        if (documentFront == null || documentFront.isEmpty()) {
            throw new BusinessException("documentFront is required");
        }
        if (selfie == null || selfie.isEmpty()) {
            throw new BusinessException("selfie is required");
        }

        validateFileType(documentFront, ALLOWED_DOCUMENT_TYPES, "documentFront");
        validateFileType(selfie, ALLOWED_IMAGE_TYPES, "selfie");
        if (documentBack != null && !documentBack.isEmpty()) {
            validateFileType(documentBack, ALLOWED_DOCUMENT_TYPES, "documentBack");
        }

        try {
            byte[] frontBytes = documentFront.getBytes();
            String frontKey = buildObjectKey(accountHolderId, verificationId, "document-front", documentFront);
            objectStorageService.putObject(kycBucket, frontKey, frontBytes, resolveContentType(documentFront));

            String backKey = null;
            if (documentBack != null && !documentBack.isEmpty()) {
                byte[] backBytes = documentBack.getBytes();
                backKey = buildObjectKey(accountHolderId, verificationId, "document-back", documentBack);
                objectStorageService.putObject(kycBucket, backKey, backBytes, resolveContentType(documentBack));
            }

            byte[] selfieBytes = selfie.getBytes();
            String selfieKey = buildObjectKey(accountHolderId, verificationId, "selfie", selfie);
            objectStorageService.putObject(kycBucket, selfieKey, selfieBytes, resolveContentType(selfie));

            VerificationDocumentEntity doc = new VerificationDocumentEntity();
            doc.setVerification(verification);
            doc.setDocumentFrontKey(frontKey);
            doc.setDocumentBackKey(backKey);
            doc.setSelfieKey(selfieKey);
            doc.setBucketName(kycBucket);
            doc.setCaptureDevice(blankToNull(captureDevice));
            doc.setAppVersion(blankToNull(appVersion));
            doc.setPlatform(blankToNull(platform));
            verificationDocumentRepository.save(doc);

            verification.setStatus(VerificationStatus.PROCESSING);
            verification.setCurrentStep(VerificationStep.DOCUMENT_ANALYSIS);
            identityVerificationRepository.save(verification);

            // Published after this transaction commits — picked up by VerificationFlowService
            eventPublisher.publishEvent(new VerificationProcessingEvent(verificationId));

            log.info("verification upload complete accountHolderId={} verificationId={} frontKey={} selfieKey={}",
                    accountHolderId, verificationId, frontKey, selfieKey);

            return new UploadVerificationResponse(verificationId, VerificationStatus.PROCESSING);

        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("verification upload failed accountHolderId={} verificationId={} error={}",
                    accountHolderId, verificationId, ex.getMessage(), ex);
            throw new BusinessException("Failed to upload verification files");
        }
    }

    @Transactional(readOnly = true)
    public VerificationResponse getVerification(UUID accountHolderId, UUID verificationId) {
        IdentityVerificationEntity verification = requireAccountHolderVerification(accountHolderId, verificationId);
        Optional<VerificationAnalysisEntity> analysis = verificationAnalysisRepository.findByVerification(verification);
        return toClientResponse(verification, analysis.orElse(null));
    }

    @Transactional(readOnly = true)
    public List<IdentityVerificationEntity> listVerifications(VerificationStatus status) {
        return status == null
                ? identityVerificationRepository.findAllByOrderByCreatedAtDesc()
                : identityVerificationRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public VerificationResponse reviewVerification(UUID verificationId, AdminVerificationReviewRequest request) {
        if (request.status() != VerificationStatus.APPROVED
                && request.status() != VerificationStatus.REJECTED
                && request.status() != VerificationStatus.REVIEW) {
            throw new BusinessException("Review status must be APPROVED, REJECTED, or REVIEW");
        }

        IdentityVerificationEntity verification = identityVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Identity verification not found"));

        verification.setStatus(request.status());
        verification.setDecisionReason(request.reason() != null ? request.reason() : "MANUALLY_REVIEWED");
        if (request.status() == VerificationStatus.APPROVED) {
            verification.setReliabilityScore(100);
        }
        verification.setCurrentStep(VerificationStep.COMPLETED);
        identityVerificationRepository.save(verification);

        // Propagate manual decision to account holder KYC status
        UUID accountHolderId = verification.getAccountHolderId();
        switch (request.status()) {
            case APPROVED -> kycReviewService.approveKyc(accountHolderId);
            case REJECTED -> kycReviewService.rejectKyc(accountHolderId);
            case REVIEW   -> kycReviewService.markPending(accountHolderId);
            default -> {}
        }

        Optional<VerificationAnalysisEntity> analysis = verificationAnalysisRepository.findByVerification(verification);
        return toAdminResponse(verification, analysis.orElse(null));
    }

    public VerificationResponse toAdminResponse(IdentityVerificationEntity verification, VerificationAnalysisEntity analysis) {
        return buildResponse(verification, analysis, false);
    }

    public VerificationResponse toClientResponse(IdentityVerificationEntity verification, VerificationAnalysisEntity analysis) {
        return buildResponse(verification, analysis, true);
    }

    private VerificationResponse buildResponse(IdentityVerificationEntity verification,
                                               VerificationAnalysisEntity analysis,
                                               boolean maskSensitiveData) {
        DocumentAnalysisDto documentAnalysis = null;
        FaceMatchDto faceMatch = null;
        ScoreDetailsDto scoreDetails = null;
        VerificationDocumentEntity verificationDocument = verificationDocumentRepository
                .findByVerification(verification)
                .orElse(null);

        if (analysis != null) {
            String documentNumber = analysis.getExtractedDocumentNumber();
            if (maskSensitiveData && documentNumber != null) {
                documentNumber = maskDocumentNumber(documentNumber);
            }

            documentAnalysis = new DocumentAnalysisDto(
                    analysis.getDocumentReadable(),
                    analysis.getDocumentExpired(),
                    verification.getDocumentType() != null ? verification.getDocumentType().name() : null,
                    documentNumber,
                    analysis.getExtractedFullName(),
                    analysis.getExtractedBirthDate() != null ? analysis.getExtractedBirthDate().toString() : null,
                    analysis.getExtractedExpiryDate() != null ? analysis.getExtractedExpiryDate().toString() : null
            );

            if (analysis.getFaceMatchScore() != null) {
                faceMatch = new FaceMatchDto(analysis.getFaceMatched(), analysis.getFaceMatchScore());
            }

            scoreDetails = parseScoreDetails(analysis.getScoreDetailsJson());
        }

        return new VerificationResponse(
                verification.getId(),
                verification.getStatus(),
                verification.getCurrentStep(),
                documentAnalysis,
                faceMatch,
                verification.getReliabilityScore(),
                scoreDetails,
                verification.getDecisionReason(),
                createSignedDownloadUrl(verificationDocument, verificationDocument == null ? null : verificationDocument.getDocumentFrontKey()),
                createSignedDownloadUrl(verificationDocument, verificationDocument == null ? null : verificationDocument.getDocumentBackKey()),
                createSignedDownloadUrl(verificationDocument, verificationDocument == null ? null : verificationDocument.getSelfieKey()),
                verification.getCreatedAt(),
                verification.getUpdatedAt()
        );
    }

    private String createSignedDownloadUrl(VerificationDocumentEntity verificationDocument, String objectKey) {
        if (verificationDocument == null || objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            return objectStorageService
                    .createDownloadUrl(
                            verificationDocument.getBucketName(),
                            objectKey,
                            Duration.ofMinutes(downloadUrlTtlMinutes)
                    )
                    .url();
        } catch (Exception ex) {
            log.warn("verification media signed url generation failed verificationId={} key={} reason={}",
                    verificationDocument.getVerification().getId(),
                    objectKey,
                    ex.getMessage());
            return null;
        }
    }

    private IdentityVerificationEntity requireAccountHolderVerification(UUID accountHolderId, UUID verificationId) {
        return identityVerificationRepository.findByIdAndAccountHolderId(verificationId, accountHolderId)
                .orElseThrow(() -> new NotFoundException("Identity verification not found"));
    }

    private void validateFileType(MultipartFile file, List<String> allowed, String fieldName) {
        String contentType = file.getContentType();
        if (contentType == null || !allowed.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Unsupported file format for " + fieldName + ". Allowed: " + allowed);
        }
    }

    private String buildObjectKey(UUID accountHolderId, UUID verificationId, String slot, MultipartFile file) {
        String ext = extensionFor(resolveContentType(file));
        return accountHolderId + "/" + verificationId + "/" + slot + "-" + OffsetDateTime.now().toEpochSecond() + ext;
    }

    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        return (ct == null || ct.isBlank()) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : ct;
    }

    private String extensionFor(String contentType) {
        if (contentType == null) return "";
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private String maskDocumentNumber(String documentNumber) {
        if (documentNumber.length() <= 4) return "****";
        int visibleChars = Math.min(2, documentNumber.length() / 3);
        int maskedLength = documentNumber.length() - visibleChars * 2;
        return documentNumber.substring(0, visibleChars)
                + "*".repeat(maskedLength)
                + documentNumber.substring(documentNumber.length() - visibleChars);
    }

    private ScoreDetailsDto parseScoreDetails(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Integer> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new ScoreDetailsDto(
                    map.get("documentReadability"),
                    map.get("ocrConfidence"),
                    map.get("documentValidity"),
                    map.get("faceMatch")
            );
        } catch (Exception ex) {
            log.warn("verification failed to parse score details: {}", ex.getMessage());
            return null;
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
