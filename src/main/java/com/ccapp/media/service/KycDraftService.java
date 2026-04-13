package com.ccapp.media.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.media.domain.dto.KycDraftResponse;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles the KYC draft snapshot that lets the mobile client restore
 * a partially completed KYC flow without re-entering data.
 *
 * <p>No writes are performed here — this is a read-only projection of
 * fields already persisted by {@link AccountHolderService#declareIdentity}
 * and {@link KycDocumentRepository}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycDraftService {

    private final AccountHolderService accountHolderService;
    private final KycDocumentRepository kycDocumentRepository;

    /**
     * Returns a {@link KycDraftResponse} for the given account holder.
     *
     * <p>The response always succeeds as long as the account holder exists;
     * callers should interpret {@code identityDeclared = false} as "user has
     * not yet completed step 1" rather than as an error.
     */
    @Transactional(readOnly = true)
    public KycDraftResponse getDraft(UUID accountHolderId) {
        log.info("KycDraftService getDraft accountHolderId={}", accountHolderId);

        AccountHolder ah = accountHolderService.getById(accountHolderId);

        // Build mediaId map — keep the most-recent entry per type when duplicates exist.
        List<KycDocument> docs = kycDocumentRepository.findByAccountHolderId(accountHolderId);
        Map<KycDocumentType, UUID> uploadedMediaIds = docs.stream()
                .collect(Collectors.toMap(
                        KycDocument::getDocumentType,
                        KycDocument::getMediaFileId,
                        (existing, replacement) -> replacement   // last-write wins
                ));

        // Identity is considered declared only when all four KYC-specific fields
        // are present (firstName/lastName exist from account creation, so they
        // alone are not a reliable discriminator).
        boolean identityDeclared = ah.getBirthDate() != null
                && ah.getDocumentNumber() != null
                && ah.getNationality() != null
                && ah.getDeclaredDocumentType() != null;

        log.info("KycDraftService getDraft accountHolderId={} identityDeclared={} uploadedDocs={} kycStatus={}",
                accountHolderId, identityDeclared, uploadedMediaIds.size(), ah.getKycStatus());

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
                identityDeclared
        );
    }
}
