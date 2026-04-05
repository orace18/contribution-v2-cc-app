package com.ccapp.identity.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycStatusConsistencyService {

    private final AccountHolderRepository accountHolderRepository;
    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;

    @Transactional
    public KycStatus reconcileAccountHolderStatus(UUID accountHolderId) {
        if (accountHolderId == null) {
            return KycStatus.NOT_STARTED;
        }

        AccountHolder accountHolder = accountHolderRepository.findById(accountHolderId).orElse(null);
        User user = userRepository.findById(accountHolderId).orElse(null);

        KycStatus storedStatus = accountHolder != null && accountHolder.getKycStatus() != null
                ? accountHolder.getKycStatus()
                : (user != null && user.getKycStatus() != null ? user.getKycStatus() : KycStatus.NOT_STARTED);
        KycStatus resolvedStatus = resolveStatus(accountHolderId, storedStatus);
        KycLevel resolvedLevel = resolveLevel(accountHolder, resolvedStatus);

        if (accountHolder != null) {
            boolean accountHolderChanged = false;
            if (accountHolder.getKycStatus() != resolvedStatus) {
                log.warn(
                        "Reconciling account-holder KYC status accountHolderId={} from={} to={}",
                        accountHolderId,
                        accountHolder.getKycStatus(),
                        resolvedStatus
                );
                accountHolder.setKycStatus(resolvedStatus);
                accountHolderChanged = true;
            }
            if (accountHolder.resolveKycLevel() != resolvedLevel) {
                log.warn(
                        "Reconciling account-holder KYC level accountHolderId={} from={} to={}",
                        accountHolderId,
                        accountHolder.resolveKycLevel(),
                        resolvedLevel
                );
                accountHolder.setKycLevel(resolvedLevel);
                accountHolderChanged = true;
            }
            if (accountHolderChanged) {
                accountHolderRepository.save(accountHolder);
            }
        }

        if (user != null && user.getKycStatus() != resolvedStatus) {
            log.warn(
                    "Reconciling user KYC status accountHolderId={} from={} to={}",
                    accountHolderId,
                    user.getKycStatus(),
                    resolvedStatus
            );
            user.setKycStatus(resolvedStatus);
            userRepository.save(user);
        }

        return resolvedStatus;
    }

    @Transactional(readOnly = true)
    public KycLevel resolveAccountHolderLevel(UUID accountHolderId) {
        if (accountHolderId == null) {
            return KycLevel.LEVEL_0_UNVERIFIED;
        }
        return accountHolderRepository.findById(accountHolderId)
                .map(AccountHolder::resolveKycLevel)
                .orElse(KycLevel.LEVEL_0_UNVERIFIED);
    }

    private KycStatus resolveStatus(UUID accountHolderId, KycStatus storedStatus) {
        KycStatus normalizedStatus = storedStatus == null ? KycStatus.NOT_STARTED : storedStatus;
        if (normalizedStatus != KycStatus.PENDING) {
            return normalizedStatus;
        }
        return hasMinimalSubmittedKyc(accountHolderId) ? KycStatus.PENDING : KycStatus.NOT_STARTED;
    }

    private KycLevel resolveLevel(AccountHolder accountHolder, KycStatus resolvedStatus) {
        if (accountHolder != null && accountHolder.getKycLevel() != null) {
            return accountHolder.getKycLevel();
        }
        return resolvedStatus == KycStatus.VERIFIED
                ? KycLevel.LEVEL_1_BASIC
                : KycLevel.LEVEL_0_UNVERIFIED;
    }

    private boolean hasMinimalSubmittedKyc(UUID accountHolderId) {
        Set<KycDocumentType> submittedTypes = kycDocumentRepository.findByAccountHolderId(accountHolderId).stream()
                .filter(this::isAcceptableForSubmission)
                .map(KycDocument::getDocumentType)
                .collect(Collectors.toSet());

        return submittedTypes.containsAll(EnumSet.of(
                KycDocumentType.IDENTITY_FRONT,
                KycDocumentType.IDENTITY_BACK,
                KycDocumentType.SELFIE
        ));
    }

    private boolean isAcceptableForSubmission(KycDocument document) {
        return document.getStatus() == KycDocumentStatus.SUBMITTED
                || document.getStatus() == KycDocumentStatus.VERIFIED;
    }
}
