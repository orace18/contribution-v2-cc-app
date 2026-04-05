package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.cpass.kyc.CPassKycGateway;
import com.ccapp.cpass.kyc.CPassKycInitRequest;
import com.ccapp.cpass.kyc.CPassKycInitResponse;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.domain.enums.KycStatus;
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
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
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

    private final KycDocumentRepository kycDocumentRepository;
    private final MediaFileRepository mediaFileRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final DelegatedActionService delegatedActionService;
    private final KycReviewService kycReviewService;
    private final ObjectStorageService objectStorageService;

    @Nullable
    @Autowired(required = false)
    private CPassKycGateway cPassKycGateway;

    @Value("${cpass.kyc.requested-level:LEVEL_1}")
    private String requestedKycLevel;

    @Value("${media.download-url-ttl-minutes:15}")
    private long downloadUrlTtlMinutes;

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
            return new SubmitKycResponse(targetAccountHolderId, KycStatus.VERIFIED, submittedAt);
        }

        Map<KycDocumentType, KycDocument> submissionDocuments = resolveMinimalKycDocuments(targetAccountHolderId);
        if (accountHolder.getKycStatus() != KycStatus.PENDING) {
            CPassKycInitResponse response = submitToCpass(accountHolder, submissionDocuments);
            applyCpassStatus(accountHolder.getId(), response.status());
        }

        delegatedActionService.logIfDelegatedActor(
                targetAccountHolderId,
                actorUserId,
                DelegatedActionType.KYC_SUBMITTED,
                "{\"submittedAt\":\"" + submittedAt + "\"}"
        );

        KycStatus finalStatus = accountHolderRepository.findById(targetAccountHolderId)
                .map(AccountHolder::getKycStatus)
                .orElse(accountHolder.getKycStatus());
        return new SubmitKycResponse(targetAccountHolderId, finalStatus, submittedAt);
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

    private CPassKycInitResponse submitToCpass(AccountHolder accountHolder,
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
            cPassKycGateway.submitDocument(
                    caseId,
                    requiredType.name(),
                    createCpassUploadResource(mediaFile, downloadUrl.url()),
                    Map.of(
                            "accountHolderId", accountHolder.getId().toString(),
                            "mediaFileId", mediaFile.getId().toString()
                    )
            );
        }
        return response;
    }

    private void applyCpassStatus(UUID accountHolderId, String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            kycReviewService.markPending(accountHolderId);
            return;
        }
        String normalizedStatus = providerStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedStatus.contains("VERIFIED") || normalizedStatus.contains("APPROVED") || normalizedStatus.contains("COMPLETED")) {
            kycReviewService.approveKyc(accountHolderId);
            return;
        }
        if (normalizedStatus.contains("RESUBMIT") || normalizedStatus.contains("RETRY")) {
            kycReviewService.requestResubmission(accountHolderId);
            return;
        }
        if (normalizedStatus.contains("REJECT") || normalizedStatus.contains("DECLINED") || normalizedStatus.contains("FAILED")) {
            kycReviewService.rejectKyc(accountHolderId);
            return;
        }
        kycReviewService.markPending(accountHolderId);
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
}
