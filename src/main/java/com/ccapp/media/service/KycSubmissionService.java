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
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycSubmissionService {

    private static final String CROSS_COUNTRY_DELEGATION_NOT_ALLOWED = "CROSS_COUNTRY_DELEGATION_NOT_ALLOWED";
    private static final String MINIMAL_KYC_INCOMPLETE = "MINIMAL_KYC_INCOMPLETE";

    private final KycDocumentRepository kycDocumentRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final DelegatedActionService delegatedActionService;
    private final KycReviewService kycReviewService;

    @Nullable
    @Autowired(required = false)
    private CPassKycGateway cPassKycGateway;

    @Value("${cpass.kyc.requested-level:LEVEL_1}")
    private String requestedKycLevel;

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

        assertMinimalKycDocuments(targetAccountHolderId);

        if (accountHolder.getKycStatus() != KycStatus.VERIFIED) {
            kycReviewService.markPending(targetAccountHolderId);
            accountHolder = accountHolderRepository.findById(targetAccountHolderId)
                    .orElseThrow(() -> new NotFoundException("Account holder not found"));
        }

        OffsetDateTime submittedAt = OffsetDateTime.now();
        delegatedActionService.logIfDelegatedActor(
                targetAccountHolderId,
                actorUserId,
                DelegatedActionType.KYC_SUBMITTED,
                "{\"submittedAt\":\"" + submittedAt + "\"}"
        );

        submitToCpassIfConfigured(accountHolder);

        KycStatus finalStatus = accountHolderRepository.findById(targetAccountHolderId)
                .map(AccountHolder::getKycStatus)
                .orElse(accountHolder.getKycStatus());
        return new SubmitKycResponse(targetAccountHolderId, finalStatus, submittedAt);
    }

    private void assertMinimalKycDocuments(UUID accountHolderId) {
        Set<KycDocumentType> submittedTypes = kycDocumentRepository.findByAccountHolderId(accountHolderId).stream()
                .filter(this::isAcceptableForSubmission)
                .map(KycDocument::getDocumentType)
                .collect(java.util.stream.Collectors.toSet());

        Set<KycDocumentType> required = EnumSet.of(
                KycDocumentType.IDENTITY_FRONT,
                KycDocumentType.IDENTITY_BACK,
                KycDocumentType.SELFIE
        );
        if (!submittedTypes.containsAll(required)) {
            throw new BusinessException(MINIMAL_KYC_INCOMPLETE);
        }
    }

    private boolean isAcceptableForSubmission(KycDocument document) {
        return document.getStatus() == KycDocumentStatus.SUBMITTED
                || document.getStatus() == KycDocumentStatus.VERIFIED;
    }

    private void submitToCpassIfConfigured(AccountHolder accountHolder) {
        if (cPassKycGateway == null) {
            return;
        }
        try {
            CPassKycInitResponse response = cPassKycGateway.checkOrInitKyc(new CPassKycInitRequest(
                    accountHolder.getId().toString(),
                    buildFullName(accountHolder),
                    accountHolder.getPhoneNumber(),
                    null,
                    requestedKycLevel
            ));
            applyCpassStatus(accountHolder.getId(), response.status());
        } catch (RuntimeException ex) {
            log.warn("KYC submission kept pending after CPASS error accountHolderId={} reason={}",
                    accountHolder.getId(), ex.getMessage());
        }
    }

    private void applyCpassStatus(UUID accountHolderId, String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
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
        }
    }

    private String buildFullName(AccountHolder accountHolder) {
        String firstName = accountHolder.getFirstName() == null ? "" : accountHolder.getFirstName().trim();
        String lastName = accountHolder.getLastName() == null ? "" : accountHolder.getLastName().trim();
        return (firstName + " " + lastName).trim();
    }
}
