package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.cpass.kyc.CPassKycDocumentResponse;
import com.ccapp.cpass.kyc.CPassKycGateway;
import com.ccapp.cpass.kyc.CPassKycInitRequest;
import com.ccapp.cpass.kyc.CPassKycInitResponse;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.media.domain.dto.SubmitKycResponse;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycSubmissionService {

    private static final String CROSS_COUNTRY_DELEGATION_NOT_ALLOWED = "CROSS_COUNTRY_DELEGATION_NOT_ALLOWED";
    private static final String MINIMAL_KYC_INCOMPLETE = "MINIMAL_KYC_INCOMPLETE";
    private static final String CPASS_KYC_UNAVAILABLE = "CPASS_KYC_UNAVAILABLE";
    private static final String CPASS_KYC_DOCUMENT_TOO_LARGE = "CPASS_KYC_DOCUMENT_TOO_LARGE";
    private static final String CPASS_KYC_DOCUMENT_IMAGE_UNREADABLE = "CPASS_KYC_DOCUMENT_IMAGE_UNREADABLE";
    private static final String KYC_LEVEL_1_AUTO_APPROVED_EVENT = "KYC_LEVEL_1_AUTO_APPROVED";

    private final KycDocumentResolutionService kycDocumentResolutionService;
    private final AccountHolderRepository accountHolderRepository;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final DelegatedActionService delegatedActionService;
    private final KycReviewService kycReviewService;
    private final KycNotificationService kycNotificationService;
    private final ObjectStorageService objectStorageService;
    private final FinancialAuditService financialAuditService;
    private final UserProfileRepository userProfileRepository;
    private final KycMediaBackupService kycMediaBackupService;

    @Nullable
    @Autowired(required = false)
    private CPassKycGateway cPassKycGateway;

    @Value("${cpass.kyc.requested-level:LEVEL_1}")
    private String requestedKycLevel;

    @Value("${cpass.kyc.level1.face-match-threshold:0.75}")
    private double level1FaceMatchThreshold;

    @Value("${cpass.kyc.document-max-bytes:950000}")
    private int cpassDocumentMaxBytes;

    @Value("${cpass.kyc.image-max-dimension:1600}")
    private int cpassImageMaxDimension;

    @Value("${cpass.kyc.image-jpeg-quality:0.82}")
    private float cpassImageJpegQuality;

    @Transactional
    public SubmitKycResponse submit(UUID actorUserId, UUID actorAccountHolderId, UUID targetAccountHolderId) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }
        accessControlService.assertCanActOnAccountHolder(actorUserId, targetAccountHolderId, PermissionLevel.FULL);

        CountryContext actorContext = countryContextService.resolve(actorAccountHolderId);
        CountryContext targetContext = countryContextService.resolve(targetAccountHolderId);
        if (!actorContext.serviceCountryCode().equalsIgnoreCase(targetContext.serviceCountryCode())) {
            throw new BusinessException(CROSS_COUNTRY_DELEGATION_NOT_ALLOWED);
        }

        AccountHolder accountHolder = accountHolderRepository.findById(targetAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        OffsetDateTime submittedAt = OffsetDateTime.now();
        if (accountHolder.getKycStatus() == KycStatus.VERIFIED) {
            return buildResponse(accountHolder, submittedAt, null);
        }

        Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> submissionDocuments =
                resolveMinimalKycDocuments(targetAccountHolderId);
        if (accountHolder.getKycStatus() != KycStatus.PENDING) {
            log.info("kyc submit triggering CPaaS accountHolderId={} priorStatus={}", accountHolder.getId(), accountHolder.getKycStatus());
            CpassSubmissionResult submissionResult = submitToCpass(accountHolder, submissionDocuments);

            // Persist caseId immediately so GET /kyc/draft can surface it after an app reload.
            String caseId = submissionResult.initResponse() != null
                    ? normalizeOptional(submissionResult.initResponse().kycCaseId())
                    : null;
            if (caseId != null) {
                accountHolder.setCpassCaseId(caseId);
                log.info("kyc submit caseId persisted accountHolderId={} caseId={}", accountHolder.getId(), caseId);
            }

            kycNotificationService.notifySubmitted(targetAccountHolderId);
            applyCpassStatus(accountHolder, submissionResult);

            delegatedActionService.logIfDelegatedActor(
                    targetAccountHolderId,
                    actorUserId,
                    DelegatedActionType.KYC_SUBMITTED,
                    "{\"submittedAt\":\"" + submittedAt + "\"}"
            );

            AccountHolder refreshedAccountHolder = accountHolderRepository.findById(targetAccountHolderId)
                    .orElse(accountHolder);
            return buildResponse(refreshedAccountHolder, submittedAt, caseId);
        } else {
            // Status is already PENDING from a prior submission — CPaaS was already called.
            // Return the persisted caseId so the mobile can start enriched polling immediately.
            String existingCaseId = normalizeOptional(accountHolder.getCpassCaseId());

            // Legacy dossiers submitted before cpass_case_id column existed have existingCaseId=null.
            // Re-probe CPaaS to recover the case ID — checkOrInitKyc is idempotent and safe to call
            // repeatedly: it returns the existing case rather than creating a duplicate.
            if (existingCaseId == null && cPassKycGateway != null) {
                try {
                    CPassKycInitResponse recovery = cPassKycGateway.checkOrInitKyc(new CPassKycInitRequest(
                            accountHolder.getId().toString(),
                            buildFullName(accountHolder),
                            accountHolder.getPhoneNumber(),
                            null,
                            requestedKycLevel
                    ));
                    existingCaseId = normalizeOptional(recovery.kycCaseId());
                    if (existingCaseId != null) {
                        accountHolder.setCpassCaseId(existingCaseId);
                        log.info("kyc submit legacy caseId recovered accountHolderId={} caseId={}",
                                accountHolder.getId(), existingCaseId);
                    } else {
                        log.warn("kyc submit legacy caseId recovery returned no caseId accountHolderId={}",
                                accountHolder.getId());
                    }
                } catch (Exception e) {
                    log.warn("kyc submit legacy caseId recovery failed accountHolderId={} reason={}",
                            accountHolder.getId(), e.getMessage());
                }
            }

            log.info("kyc submit already PENDING, returning persisted caseId accountHolderId={} caseId={}",
                    accountHolder.getId(), existingCaseId);

            delegatedActionService.logIfDelegatedActor(
                    targetAccountHolderId,
                    actorUserId,
                    DelegatedActionType.KYC_SUBMITTED,
                    "{\"submittedAt\":\"" + submittedAt + "\"}"
            );

            AccountHolder refreshedAccountHolder = accountHolderRepository.findById(targetAccountHolderId)
                    .orElse(accountHolder);
            return buildResponse(refreshedAccountHolder, submittedAt, existingCaseId);
        }
    }

    private Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> resolveMinimalKycDocuments(UUID accountHolderId) {
        Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> submittedDocuments =
                kycDocumentResolutionService.resolveActiveDocuments(accountHolderId);

        EnumSet<KycDocumentType> required = EnumSet.of(
                KycDocumentType.IDENTITY_FRONT,
                KycDocumentType.IDENTITY_BACK,
                KycDocumentType.SELFIE
        );
        if (!submittedDocuments.keySet().containsAll(required)) {
            throw new BusinessException(MINIMAL_KYC_INCOMPLETE);
        }
        for (KycDocumentType documentType : required) {
            KycDocumentResolutionService.ResolvedKycDocument resolvedDocument = submittedDocuments.get(documentType);
            log.info(
                    "kyc submit resolved media accountHolderId={} documentSlot={} mediaId={} source={} bucket={} objectKey={} finalUrl={}",
                    accountHolderId,
                    documentType,
                    resolvedDocument.mediaFile().getId(),
                    resolvedDocument.source(),
                    resolvedDocument.mediaFile().getBucket(),
                    resolvedDocument.mediaFile().getObjectKey(),
                    resolvedDocument.finalUrl()
            );
        }
        return submittedDocuments;
    }

    private CpassSubmissionResult submitToCpass(AccountHolder accountHolder,
                                                Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> submissionDocuments) {
        if (cPassKycGateway == null) {
            throw new BusinessException(CPASS_KYC_UNAVAILABLE);
        }

        CPassKycInitResponse response = cPassKycGateway.checkOrInitKyc(new CPassKycInitRequest(
                accountHolder.getId().toString(),
                buildFullName(accountHolder),
                accountHolder.getPhoneNumber(),
                null,
                requestedKycLevel
        ));

        String caseId = normalizeOptional(response.kycCaseId());
        if (caseId == null) {
            throw new BusinessException("CPASS_KYC_CASE_ID_MISSING");
        }

        List<CPassKycDocumentResponse> documentResponses = new ArrayList<>();
        for (KycDocumentType requiredType : EnumSet.of(
                KycDocumentType.IDENTITY_FRONT,
                KycDocumentType.IDENTITY_BACK,
                KycDocumentType.SELFIE
        )) {
            KycDocumentResolutionService.ResolvedKycDocument resolvedDocument = submissionDocuments.get(requiredType);
            MediaFile mediaFile = resolvedDocument.mediaFile();
            PreparedCpassUpload preparedUpload = createCpassUploadResource(requiredType, mediaFile);
            log.info(
                    "kyc submit sending document accountHolderId={} documentSlot={} mediaId={} source={} bucket={} objectKey={} finalUrl={} originalBytes={} uploadBytes={} uploadContentType={} uploadFilename={} transformed={}",
                    accountHolder.getId(),
                    requiredType,
                    mediaFile.getId(),
                    resolvedDocument.source(),
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    resolvedDocument.finalUrl(),
                    preparedUpload.originalBytes(),
                    preparedUpload.uploadBytes(),
                    preparedUpload.contentType(),
                    preparedUpload.filename(),
                    preparedUpload.transformed()
            );
            CPassKycDocumentResponse documentResponse = cPassKycGateway.submitDocument(
                    caseId,
                    toCpassDocumentType(requiredType, accountHolder.getDeclaredDocumentType()),
                    preparedUpload.resource(),
                    Map.of(
                            "accountHolderId", accountHolder.getId().toString(),
                            "mediaFileId", mediaFile.getId().toString()
                    )
            );
            documentResponses.add(documentResponse);
        }
        return new CpassSubmissionResult(response, List.copyOf(documentResponses));
    }

    private void applyCpassStatus(AccountHolder accountHolder, CpassSubmissionResult submissionResult) {
        ProviderSignals signals = resolveProviderSignals(submissionResult);
        
        log.info("kyc submit provider signals evaluated accountHolderId={} documentValid={} faceMatchScore={} identityStatus={} riskLevel={}",
                 accountHolder.getId(), signals.documentValid(), signals.faceMatchScore(), signals.identityStatus(), signals.riskLevel());

        if (meetsLevel1AutoApproval(signals)) {
            applyOcrProfileData(accountHolder, signals);
            kycReviewService.approveKyc(accountHolder.getId(), KycLevel.LEVEL_1_BASIC);
            recordLevel1AutoApprovedAudit(accountHolder.getId(), signals);
            return;
        }

        String providerStatus = firstNonBlank(
                signals.identityStatus(),
                submissionResult.initResponse().status(),
                latestDocumentStatus(submissionResult.documentResponses())
        );
        
        log.info("kyc submit fallback providerStatus accountHolderId={} status={}", accountHolder.getId(), providerStatus);

        if (providerStatus == null || providerStatus.isBlank()) {
            kycReviewService.markPending(accountHolder.getId());
            return;
        }
        String normalizedStatus = providerStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedStatus.contains("RESUBMIT") || normalizedStatus.contains("RETRY")) {
            kycReviewService.requestResubmission(accountHolder.getId());
            return;
        }
        if (normalizedStatus.contains("REJECT") || normalizedStatus.contains("DECLINED") || normalizedStatus.contains("FAILED")) {
            kycReviewService.rejectKyc(accountHolder.getId());
            return;
        }
        kycReviewService.markPending(accountHolder.getId());
    }

    private String buildFullName(AccountHolder accountHolder) {
        String firstName = accountHolder.getFirstName() == null ? "" : accountHolder.getFirstName().trim();
        String lastName = accountHolder.getLastName() == null ? "" : accountHolder.getLastName().trim();
        return (firstName + " " + lastName).trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SubmitKycResponse buildResponse(AccountHolder accountHolder, OffsetDateTime submittedAt, String caseId) {
        return new SubmitKycResponse(
                accountHolder.getId(),
                accountHolder.getKycStatus(),
                accountHolder.resolveKycLevel(),
                submittedAt,
                caseId,
                null, null, null, null, null, null
        );
    }

    private ProviderSignals resolveProviderSignals(CpassSubmissionResult submissionResult) {
        boolean allDocumentsValid = !submissionResult.documentResponses().isEmpty()
                && submissionResult.documentResponses().stream().allMatch(this::isDocumentMarkedValid);
        boolean hasExplicitDocumentValidity = submissionResult.documentResponses().stream()
                .map(CPassKycDocumentResponse::documentValid)
                .anyMatch(Objects::nonNull);

        Boolean documentValid = hasExplicitDocumentValidity
                ? submissionResult.documentResponses().stream()
                .map(CPassKycDocumentResponse::documentValid)
                .filter(Objects::nonNull)
                .reduce(Boolean.TRUE, Boolean::logicalAnd)
                : submissionResult.initResponse().documentValid();
        if (documentValid == null && allDocumentsValid) {
            documentValid = true;
        }

        Double faceMatchScore = submissionResult.documentResponses().stream()
                .map(CPassKycDocumentResponse::faceMatchScore)
                .filter(Objects::nonNull)
                .reduce(null, (current, candidate) -> current == null || candidate > current ? candidate : current);
        if (faceMatchScore == null) {
            faceMatchScore = submissionResult.initResponse().faceMatchScore();
        }

        return new ProviderSignals(
                documentValid,
                faceMatchScore,
                normalizeOptional(lastNonBlank(
                        submissionResult.documentResponses().stream()
                                .map(CPassKycDocumentResponse::identityStatus)
                                .toList(),
                        submissionResult.initResponse().identityStatus()
                )),
                normalizeOptional(lastNonBlank(
                        submissionResult.documentResponses().stream()
                                .map(CPassKycDocumentResponse::riskLevel)
                                .toList(),
                        submissionResult.initResponse().riskLevel()
                )),
                normalizeOptional(lastNonBlank(
                        submissionResult.documentResponses().stream()
                                .map(CPassKycDocumentResponse::firstName)
                                .toList(),
                        submissionResult.initResponse().firstName()
                )),
                normalizeOptional(lastNonBlank(
                        submissionResult.documentResponses().stream()
                                .map(CPassKycDocumentResponse::lastName)
                                .toList(),
                        submissionResult.initResponse().lastName()
                )),
                submissionResult.documentResponses().stream()
                        .map(CPassKycDocumentResponse::birthDate)
                        .filter(Objects::nonNull)
                        .reduce((first, second) -> second)
                        .orElse(submissionResult.initResponse().birthDate()),
                normalizeOptional(lastNonBlank(
                        submissionResult.documentResponses().stream()
                                .map(CPassKycDocumentResponse::documentNumber)
                                .toList(),
                        submissionResult.initResponse().documentNumber()
                )),
                normalizeOptional(submissionResult.initResponse().referenceId()),
                normalizeOptional(submissionResult.initResponse().kycCaseId())
        );
    }

    private boolean meetsLevel1AutoApproval(ProviderSignals signals) {
        return Boolean.TRUE.equals(signals.documentValid())
                && signals.faceMatchScore() != null
                && signals.faceMatchScore() >= level1FaceMatchThreshold
                && matchesNormalized(signals.identityStatus(), "VERIFIED")
                && matchesNormalized(signals.riskLevel(), "LOW");
    }

    private void applyOcrProfileData(AccountHolder accountHolder, ProviderSignals signals) {
        if (signals.firstName() != null) {
            accountHolder.setFirstName(signals.firstName());
        }
        if (signals.lastName() != null) {
            accountHolder.setLastName(signals.lastName());
        }
        if (signals.birthDate() != null) {
            accountHolder.setBirthDate(signals.birthDate());
        }
        if (signals.documentNumber() != null) {
            accountHolder.setDocumentNumber(signals.documentNumber());
        }

        userProfileRepository.findByUserId(accountHolder.getId()).ifPresent(profile -> {
            if (signals.firstName() != null) {
                profile.setFirstName(signals.firstName());
            }
            if (signals.lastName() != null) {
                profile.setLastName(signals.lastName());
            }
            userProfileRepository.save(profile);
        });
    }

    private void recordLevel1AutoApprovedAudit(UUID accountHolderId, ProviderSignals signals) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kycLevel", KycLevel.LEVEL_1_BASIC.name());
        metadata.put("documentValid", Boolean.TRUE);
        metadata.put("faceMatchScore", signals.faceMatchScore());
        metadata.put("faceMatchThreshold", level1FaceMatchThreshold);
        metadata.put("identityStatus", signals.identityStatus());
        metadata.put("riskLevel", signals.riskLevel());
        if (signals.documentNumber() != null) {
            metadata.put("documentNumber", signals.documentNumber());
        }
        financialAuditService.record(
                KYC_LEVEL_1_AUTO_APPROVED_EVENT,
                firstNonBlank(signals.referenceId(), signals.caseId()),
                accountHolderId,
                null,
                metadata
        );
    }

    private boolean isDocumentMarkedValid(CPassKycDocumentResponse response) {
        if (Boolean.TRUE.equals(response.documentValid())) {
            return true;
        }
        String validityStatus = normalizeOptional(response.validityStatus());
        if (validityStatus == null) {
            return false;
        }
        String normalizedStatus = validityStatus.toUpperCase(java.util.Locale.ROOT);
        return normalizedStatus.contains("VALID")
                || normalizedStatus.contains("VERIFIED")
                || normalizedStatus.contains("APPROVED");
    }

    private boolean matchesNormalized(String actual, String expected) {
        String normalizedActual = normalizeOptional(actual);
        return normalizedActual != null && normalizedActual.equalsIgnoreCase(expected);
    }

    private String latestDocumentStatus(List<CPassKycDocumentResponse> documentResponses) {
        return documentResponses.stream()
                .map(CPassKycDocumentResponse::status)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String lastNonBlank(List<String> values, String fallback) {
        String candidate = null;
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                candidate = normalized;
            }
        }
        return candidate != null ? candidate : normalizeOptional(fallback);
    }

    private PreparedCpassUpload createCpassUploadResource(KycDocumentType documentType, MediaFile mediaFile) {
        try {
            byte[] bytes = objectStorageService.readObject(mediaFile.getBucket(), mediaFile.getObjectKey());
            String filename = extractFilename(mediaFile.getObjectKey());
            String contentType = normalizeContentType(mediaFile.getContentType(), filename);
            return prepareUpload(mediaFile, documentType, bytes, filename, contentType);
        } catch (RuntimeException ex) {
            KycMediaBackupService.StoredKycMediaBackup mediaBackup = findKycBackup(mediaFile).orElse(null);
            if (mediaBackup != null) {
                String filename = extractFilename(mediaFile.getObjectKey());
                String contentType = normalizeContentType(mediaBackup.contentType(), filename);
                log.warn(
                        "kyc submit using backup accountHolderId={} mediaId={} documentType={} bucket={} objectKey={} backupBytes={} fallbackReason={}",
                        mediaFile.getAccountHolderId(),
                        mediaFile.getId(),
                        documentType,
                        mediaFile.getBucket(),
                        mediaFile.getObjectKey(),
                        mediaBackup.payload().length,
                        ex.getMessage()
                );
                return prepareUpload(mediaFile, documentType, mediaBackup.payload(), filename, contentType);
            }
            log.error(
                    "kyc submit: media read failed accountHolderId={} mediaId={} documentType={} bucket={} objectKey={} reason={}",
                    mediaFile.getAccountHolderId(),
                    mediaFile.getId(),
                    documentType,
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    ex.getMessage(),
                    ex
            );
            throw new BusinessException("CPASS_KYC_DOCUMENT_READ_FAILED");
        }
    }

    private PreparedCpassUpload prepareUpload(MediaFile mediaFile,
                                              KycDocumentType documentType,
                                              byte[] originalBytes,
                                              String filename,
                                              String contentType) {
        if (originalBytes.length <= cpassDocumentMaxBytes) {
            return new PreparedCpassUpload(
                    byteArrayResource(originalBytes, filename),
                    filename,
                    contentType,
                    originalBytes.length,
                    originalBytes.length,
                    false
            );
        }
        if (!contentType.startsWith("image/")) {
            log.warn(
                    "kyc submit document too large and non-image accountHolderId={} documentType={} mediaId={} originalBytes={} maxBytes={} contentType={} filename={}",
                    mediaFile.getAccountHolderId(),
                    documentType,
                    mediaFile.getId(),
                    originalBytes.length,
                    cpassDocumentMaxBytes,
                    contentType,
                    filename
            );
            throw new BusinessException(CPASS_KYC_DOCUMENT_TOO_LARGE);
        }
        return compressImageForCpass(mediaFile, documentType, originalBytes, filename, contentType);
    }

    private PreparedCpassUpload compressImageForCpass(MediaFile mediaFile,
                                                      KycDocumentType documentType,
                                                      byte[] originalBytes,
                                                      String filename,
                                                      String contentType) {
        BufferedImage sourceImage = readImage(originalBytes);
        if (sourceImage == null) {
            log.warn(
                    "kyc submit image unreadable accountHolderId={} documentType={} mediaId={} originalBytes={} contentType={} filename={}",
                    mediaFile.getAccountHolderId(),
                    documentType,
                    mediaFile.getId(),
                    originalBytes.length,
                    contentType,
                    filename
            );
            throw new BusinessException(CPASS_KYC_DOCUMENT_IMAGE_UNREADABLE);
        }

        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        int largestDimension = Math.max(sourceWidth, sourceHeight);
        double scale = largestDimension > 0
                ? Math.min(1.0d, (double) cpassImageMaxDimension / largestDimension)
                : 1.0d;
        float quality = clampJpegQuality(cpassImageJpegQuality);
        byte[] smallestAttempt = originalBytes;
        int smallestWidth = sourceWidth;
        int smallestHeight = sourceHeight;

        for (int attempt = 0; attempt < 8; attempt++) {
            int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
            int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
            BufferedImage scaledImage = scaleImage(sourceImage, targetWidth, targetHeight);
            byte[] encodedBytes = encodeJpeg(scaledImage, quality);
            if (encodedBytes.length < smallestAttempt.length) {
                smallestAttempt = encodedBytes;
                smallestWidth = targetWidth;
                smallestHeight = targetHeight;
            }
            if (encodedBytes.length <= cpassDocumentMaxBytes) {
                String uploadFilename = replaceExtension(filename, ".jpg");
                log.info(
                        "kyc submit document compressed accountHolderId={} documentType={} mediaId={} originalBytes={} uploadBytes={} maxBytes={} originalContentType={} uploadContentType=image/jpeg width={} height={} quality={} filename={}",
                        mediaFile.getAccountHolderId(),
                        documentType,
                        mediaFile.getId(),
                        originalBytes.length,
                        encodedBytes.length,
                        cpassDocumentMaxBytes,
                        contentType,
                        targetWidth,
                        targetHeight,
                        quality,
                        uploadFilename
                );
                return new PreparedCpassUpload(
                        byteArrayResource(encodedBytes, uploadFilename),
                        uploadFilename,
                        "image/jpeg",
                        originalBytes.length,
                        encodedBytes.length,
                        true
                );
            }
            scale *= 0.82d;
            quality = clampJpegQuality(quality - 0.07f);
        }

        log.warn(
                "kyc submit document still too large after compression accountHolderId={} documentType={} mediaId={} originalBytes={} smallestBytes={} maxBytes={} smallestWidth={} smallestHeight={} contentType={} filename={}",
                mediaFile.getAccountHolderId(),
                documentType,
                mediaFile.getId(),
                originalBytes.length,
                smallestAttempt.length,
                cpassDocumentMaxBytes,
                smallestWidth,
                smallestHeight,
                contentType,
                filename
        );
        throw new BusinessException(CPASS_KYC_DOCUMENT_TOO_LARGE);
    }

    private java.util.Optional<KycMediaBackupService.StoredKycMediaBackup> findKycBackup(MediaFile mediaFile) {
        java.util.Optional<KycMediaBackupService.StoredKycMediaBackup> backup = kycMediaBackupService.findBackup(mediaFile);
        return backup == null ? java.util.Optional.empty() : backup;
    }

    private BufferedImage readImage(byte[] bytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(inputStream);
        } catch (IOException ex) {
            throw new BusinessException(CPASS_KYC_DOCUMENT_IMAGE_UNREADABLE);
        }
    }

    private BufferedImage scaleImage(BufferedImage sourceImage, int targetWidth, int targetHeight) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaledImage.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaledImage;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new BusinessException(CPASS_KYC_DOCUMENT_IMAGE_UNREADABLE);
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(clampJpegQuality(quality));
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            writer.dispose();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            writer.dispose();
            throw new BusinessException(CPASS_KYC_DOCUMENT_IMAGE_UNREADABLE);
        }
    }

    private ByteArrayResource byteArrayResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private String normalizeContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
        String normalizedFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".png")) {
            return "image/png";
        }
        if (normalizedFilename.endsWith(".jpg") || normalizedFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String replaceExtension(String filename, String extension) {
        if (filename == null || filename.isBlank()) {
            return "kyc-document" + extension;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return filename + extension;
        }
        return filename.substring(0, dotIndex) + extension;
    }

    private float clampJpegQuality(float quality) {
        return Math.max(0.40f, Math.min(0.95f, quality));
    }

    private String extractFilename(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "kyc-document";
        }
        int separatorIndex = objectKey.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex == objectKey.length() - 1) {
            return objectKey.trim();
        }
        return objectKey.substring(separatorIndex + 1).trim();
    }

    private record CpassSubmissionResult(
            CPassKycInitResponse initResponse,
            List<CPassKycDocumentResponse> documentResponses
    ) {
    }

    private record PreparedCpassUpload(
            ByteArrayResource resource,
            String filename,
            String contentType,
            int originalBytes,
            int uploadBytes,
            boolean transformed
    ) {
    }

    private String toCpassDocumentType(KycDocumentType documentType, String declaredDocumentType) {
        return switch (documentType) {
            case IDENTITY_FRONT, IDENTITY_BACK -> toPassportOrIdCard(declaredDocumentType);
            case SELFIE                        -> "SELFIE";
        };
    }

    private String toPassportOrIdCard(String declaredDocumentType) {
        if (declaredDocumentType != null
                && declaredDocumentType.trim().equalsIgnoreCase("PASSPORT")) {
            return "PASSPORT";
        }
        return "ID_CARD";
    }

    private record ProviderSignals(
            Boolean documentValid,
            Double faceMatchScore,
            String identityStatus,
            String riskLevel,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String documentNumber,
            String referenceId,
            String caseId
    ) {
    }
}
