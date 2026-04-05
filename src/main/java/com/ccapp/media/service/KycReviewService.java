package com.ccapp.media.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.progress.event.KycVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycReviewService {

    private final AccountHolderRepository accountHolderRepository;
    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public void approveKyc(UUID accountHolderId) {
        approveKyc(accountHolderId, KycLevel.LEVEL_1_BASIC);
    }

    @Transactional
    public void approveKyc(UUID accountHolderId, KycLevel kycLevel) {
        AccountHolder accountHolder = loadAccountHolder(accountHolderId);
        accountHolder.setKycStatus(KycStatus.VERIFIED);
        accountHolder.setKycLevel(kycLevel);
        accountHolder.setKycVerifiedAt(OffsetDateTime.now());
        accountHolderRepository.save(accountHolder);

        syncUserKycStatus(accountHolderId, KycStatus.VERIFIED);
        updateDocumentStatuses(accountHolderId, KycDocumentStatus.VERIFIED);
        domainEventPublisher.publish(KycVerifiedEvent.create(accountHolderId));
    }

    @Transactional
    public void rejectKyc(UUID accountHolderId) {
        AccountHolder accountHolder = loadAccountHolder(accountHolderId);
        accountHolder.setKycStatus(KycStatus.REJECTED);
        if (accountHolder.getKycLevel() == null) {
            accountHolder.setKycLevel(KycLevel.LEVEL_0_UNVERIFIED);
        }
        accountHolder.setKycVerifiedAt(null);
        accountHolderRepository.save(accountHolder);

        syncUserKycStatus(accountHolderId, KycStatus.REJECTED);
        updateDocumentStatuses(accountHolderId, KycDocumentStatus.REJECTED);
    }

    @Transactional
    public void requestResubmission(UUID accountHolderId) {
        AccountHolder accountHolder = loadAccountHolder(accountHolderId);
        accountHolder.setKycStatus(KycStatus.RESUBMIT_REQUIRED);
        if (accountHolder.getKycLevel() == null) {
            accountHolder.setKycLevel(KycLevel.LEVEL_0_UNVERIFIED);
        }
        accountHolder.setKycVerifiedAt(null);
        accountHolderRepository.save(accountHolder);

        syncUserKycStatus(accountHolderId, KycStatus.RESUBMIT_REQUIRED);
        updateDocumentStatuses(accountHolderId, KycDocumentStatus.REJECTED);
    }

    @Transactional
    public void markPending(UUID accountHolderId) {
        AccountHolder accountHolder = loadAccountHolder(accountHolderId);
        accountHolder.setKycStatus(KycStatus.PENDING);
        if (accountHolder.getKycLevel() == null) {
            accountHolder.setKycLevel(KycLevel.LEVEL_0_UNVERIFIED);
        }
        accountHolder.setKycVerifiedAt(null);
        accountHolderRepository.save(accountHolder);
        syncUserKycStatus(accountHolderId, KycStatus.PENDING);
    }

    private AccountHolder loadAccountHolder(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));
    }

    private void syncUserKycStatus(UUID accountHolderId, KycStatus kycStatus) {
        User user = userRepository.findById(accountHolderId).orElse(null);
        if (user == null) {
            return;
        }
        user.setKycStatus(kycStatus);
        userRepository.save(user);
    }

    private void updateDocumentStatuses(UUID accountHolderId, KycDocumentStatus targetStatus) {
        List<KycDocument> documents = kycDocumentRepository.findByAccountHolderId(accountHolderId);
        boolean changed = false;
        for (KycDocument document : documents) {
            if (document.getStatus() == KycDocumentStatus.SUBMITTED || document.getStatus() == KycDocumentStatus.VERIFIED) {
                if (document.getStatus() != targetStatus) {
                    document.setStatus(targetStatus);
                    changed = true;
                }
            }
        }
        if (changed) {
            kycDocumentRepository.saveAll(documents);
        }
    }
}
