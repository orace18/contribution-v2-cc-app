package com.ccapp.media.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.media.domain.dto.KycDraftResponse;
import com.ccapp.media.domain.enums.KycDocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles the KYC draft snapshot that lets the mobile client restore
 * a partially completed KYC flow without re-entering data.
 *
 * <p>The draft now reuses the same active-document resolution as submit so the
 * mobile client and the submit flow always point to the same current media IDs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycDraftService {

    private final AccountHolderService accountHolderService;
    private final KycDocumentResolutionService kycDocumentResolutionService;

    @Transactional
    public KycDraftResponse getDraft(UUID accountHolderId) {
        log.info("KycDraftService getDraft accountHolderId={}", accountHolderId);

        AccountHolder ah = accountHolderService.getById(accountHolderId);

        Map<KycDocumentType, UUID> uploadedMediaIds = kycDocumentResolutionService.resolveActiveDocuments(accountHolderId)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().mediaFile().getId(),
                        (existing, replacement) -> replacement,
                        () -> new EnumMap<>(KycDocumentType.class)
                ));

        boolean identityDeclared = ah.getBirthDate() != null
                && ah.getDocumentNumber() != null
                && ah.getNationality() != null
                && ah.getDeclaredDocumentType() != null;

        log.info("KycDraftService getDraft accountHolderId={} identityDeclared={} uploadedDocs={} uploadedMediaIds={} kycStatus={}",
                accountHolderId, identityDeclared, uploadedMediaIds.size(), uploadedMediaIds, ah.getKycStatus());

        return new KycDraftResponse(
                ah.getId(),
                ah.getFirstName(),
                ah.getLastName(),
                ah.getBirthDate(),
                ah.getNationality(),
                ah.getDeclaredDocumentType(),
                ah.getDocumentNumber(),
                uploadedMediaIds,
                ah.getKycStatus(),
                ah.resolveKycLevel(),
                identityDeclared,
                ah.getCpassCaseId()
        );
    }
}
