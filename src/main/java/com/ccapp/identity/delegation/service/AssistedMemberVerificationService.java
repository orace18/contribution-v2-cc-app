package com.ccapp.identity.delegation.service;

import com.ccapp.identity.delegation.domain.entity.AssistedMemberVerification;
import com.ccapp.identity.delegation.domain.enums.AssistedMemberVerificationType;
import com.ccapp.identity.delegation.repository.AssistedMemberVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssistedMemberVerificationService {

    private final AssistedMemberVerificationRepository assistedMemberVerificationRepository;

    @Transactional
    public AssistedMemberVerification recordVerification(UUID accountHolderId,
                                                         UUID verifiedBy,
                                                         AssistedMemberVerificationType verificationType) {
        return assistedMemberVerificationRepository.save(AssistedMemberVerification.builder()
                .accountHolderId(accountHolderId)
                .verifiedBy(verifiedBy)
                .verificationType(verificationType)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AssistedMemberVerification> listVerifications(UUID accountHolderId) {
        return assistedMemberVerificationRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId);
    }
}
