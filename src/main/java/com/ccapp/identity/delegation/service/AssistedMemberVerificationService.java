package com.ccapp.identity.delegation.service;

import com.ccapp.identity.delegation.domain.entity.AssistedMemberVerification;
import com.ccapp.identity.delegation.domain.enums.AssistedMemberVerificationType;
import com.ccapp.identity.delegation.repository.AssistedMemberVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistedMemberVerificationService {

    private final AssistedMemberVerificationRepository assistedMemberVerificationRepository;

    @Transactional
    public AssistedMemberVerification recordVerification(UUID accountHolderId,
                                                         UUID verifiedBy,
                                                         AssistedMemberVerificationType verificationType) {
        log.info("Identity assisted-verification record accountHolderId={} verifiedBy={} verificationType={}",
                accountHolderId, verifiedBy, verificationType);
        AssistedMemberVerification saved = assistedMemberVerificationRepository.save(AssistedMemberVerification.builder()
                .accountHolderId(accountHolderId)
                .verifiedBy(verifiedBy)
                .verificationType(verificationType)
                .build());
        log.info("Identity assisted-verification saved id={} accountHolderId={}", saved.getId(), saved.getAccountHolderId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AssistedMemberVerification> listVerifications(UUID accountHolderId) {
        log.debug("Identity assisted-verification list accountHolderId={}", accountHolderId);
        List<AssistedMemberVerification> verifications =
                assistedMemberVerificationRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId);
        log.debug("Identity assisted-verification list count accountHolderId={} count={}", accountHolderId, verifications.size());
        return verifications;
    }
}
