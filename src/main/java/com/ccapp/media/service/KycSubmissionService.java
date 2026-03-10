package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycSubmissionService {

    private static final String CROSS_COUNTRY_DELEGATION_NOT_ALLOWED = "CROSS_COUNTRY_DELEGATION_NOT_ALLOWED";
    private static final String MINIMAL_KYC_INCOMPLETE = "MINIMAL_KYC_INCOMPLETE";

    private final KycDocumentRepository kycDocumentRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final DelegatedActionService delegatedActionService;

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
            accountHolder.setKycStatus(KycStatus.PENDING);
            accountHolderRepository.save(accountHolder);
        }

        OffsetDateTime submittedAt = OffsetDateTime.now();
        delegatedActionService.logIfDelegatedActor(
                targetAccountHolderId,
                actorUserId,
                DelegatedActionType.KYC_SUBMITTED,
                "{\"submittedAt\":\"" + submittedAt + "\"}"
        );

        return new SubmitKycResponse(targetAccountHolderId, accountHolder.getKycStatus(), submittedAt);
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
}
