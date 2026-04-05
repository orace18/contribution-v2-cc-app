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
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.media.storage.PresignedUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.lang.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycSubmissionService {

    private static final String CROSS_COUNTRY_DELEGATION_NOT_ALLOWED = "CROSS_COUNTRY_DELEGATION_NOT_ALLOWED";
    private static final String MINIMAL_KYC_INCOMPLETE = "MINIMAL_KYC_INCOMPLETE";
    private static final String CPASS_KYC_UNAVAILABLE = "CPASS_KYC_UNAVAILABLE";
    private static final String KYC_DOCUMENT_MEDIA_NOT_FOUND = "KYC_DOCUMENT_MEDIA_NOT_FOUND";
    private static final String KYC_DOCUMENT_MEDIA_NOT_READY = "KYC_DOCUMENT_MEDIA_NOT_READY";
    private static final String KYC_LEVEL_1_AUTO_APPROVED_EVENT = "KYC_LEVEL_1_AUTO_APPROVED";

    private final KycDocumentRepository kycDocumentRepository;
    private final MediaFileRepository mediaFileRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final DelegatedActionService delegatedActionService;
    private final KycReviewService kycReviewService;
    private final ObjectStorageService objectStorageService;
    private final FinancialAuditService financialAuditService;
    private final UserProfileRepository userProfileRepository;

    @Nullable
    @Autowired(required = false)
    private CPassKycGateway cPassKycGateway;

    @Value("${cpass.kyc.requested-level:LEVEL_1}")
    private String requestedKycLevel;

    @Value("${media.download-url-ttl-minutes:15}")
    private long downloadUrlTtlMinutes;

    @Value("${cpass.kyc.level1.face-match-threshold:0.75}")
    private double level1FaceMatchThreshold;

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
            return buildResponse(accountHolder, submittedAt);
        }

        Map<KycDocumentType, KycDocument> submissionDocuments = resolveMinimalKycDocuments(targetAccountHolderId);
        if (accountHolder.getKycStatus() != KycStatus.PENDING) {
            CpassSubmissionResult submissionResult = submitToCpass(accountHolder, submissionDocuments);
            applyCpassStatus(accountHolder, submissionResult);
        }

        delegatedActionService.logIfDelegatedActor(
                targetAccountHolderId,
                actorUserId,
                DelegatedActionType.KYC_SUBMITTED,
                "{\"submittedAt\":\"" + submittedAt + "\"}"
        );

        AccountHolder refreshedAccountHolder = accountHolderRepository.findById(targetAccountHolderId)
                .orElse(accountHolder);
        return buildResponse(refreshedAccountHolder, submittedAt);
    }

    private Map<KycDocumentType, KycDocument> resolveMinimalKycDocuments(UUID accountHolderId) {
        Map<KycDocumentType, KycDocument> submittedDocuments = kycDocumentRepository.findByAccountHolderId(accountHolderId).stream()
                .filter(this::isAcceptableForSubmission)
                .sorted(Comparator.comparing(KycDocument::getCreatedAt))
                .collect(Collectors.toMap(
                        KycDocument::getDocumentType,
                        document -> document,
                        (previous, replacement) -> replacement,
                        () -> new EnumMap<>(KycDocumentType.class)
                ));

        Set<KycDocumentType> required = EnumSet.of(
                KycDocumentType.IDENTITY_FRONT,
                KycDocumentType.IDENTITY_BACK,
                KycDocumentType.SELFIE
        );
        if (!submittedDocuments.keySet().containsAll(required)) {
            throw new BusinessException(MINIMAL_KYC_INCOMPLETE);
        }
        return submittedDocuments;
    }

    private boolean isAcceptableForSubmission(KycDocument document) {
        return document.getStatus() == KycDocumentStatus.SUBMITTED
                || document.getStatus() == KycDocumentStatus.VERIFIED;
    }

    private CpassSubmissionResult submitToCpass(AccountHolder accountHolder,
                                                Map<KycDocumentType, KycDocument> submissionDocuments) {
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
            KycDocument document = submissionDocuments.get(requiredType);
            MediaFile mediaFile = loadReadyMediaFile(accountHolder.getId(), document.getMediaFileId());
            PresignedUrl downloadUrl = objectStorageService.createDownloadUrl(
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    Duration.ofMinutes(downloadUrlTtlMinutes)
            );
            CPassKycDocumentResponse documentResponse = cPassKycGateway.submitDocument(
                    caseId,
                    requiredType.name(),
                    createCpassUploadResource(mediaFile, downloadUrl.url()),
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

    private MediaFile loadReadyMediaFile(UUID accountHolderId, UUID mediaFileId) {
        MediaFile mediaFile = mediaFileRepository.findByIdAndAccountHolderId(mediaFileId, accountHolderId)
                .orElseThrow(() -> new BusinessException(KYC_DOCUMENT_MEDIA_NOT_FOUND));
        if (mediaFile.getStatus() != com.ccapp.media.domain.enums.MediaStatus.READY) {
            throw new BusinessException(KYC_DOCUMENT_MEDIA_NOT_READY);
        }
        return mediaFile;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SubmitKycResponse buildResponse(AccountHolder accountHolder, OffsetDateTime submittedAt) {
        return new SubmitKycResponse(
                accountHolder.getId(),
                accountHolder.getKycStatus(),
                accountHolder.resolveKycLevel(),
                submittedAt
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

    private ByteArrayResource createCpassUploadResource(MediaFile mediaFile, String downloadUrl) {
        try (java.io.InputStream inputStream = URI.create(downloadUrl).toURL().openStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String filename = extractFilename(mediaFile.getObjectKey());
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException("CPASS_KYC_DOCUMENT_READ_FAILED");
        }
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
