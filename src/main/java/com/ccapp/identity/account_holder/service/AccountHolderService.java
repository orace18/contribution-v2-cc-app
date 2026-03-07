package com.ccapp.identity.account_holder.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.KycStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountHolderService {

    private final AccountHolderRepository accountHolderRepository;

    @Transactional(readOnly = true)
    public AccountHolder getById(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID accountHolderId) {
        return accountHolderRepository.existsById(accountHolderId);
    }

    @Transactional
    public AccountHolder ensureFromLegacyUser(User user, UserProfile profile) {
        return accountHolderRepository.findById(user.getId())
                .orElseGet(() -> accountHolderRepository.save(AccountHolder.builder()
                        .id(user.getId())
                        .firstName(profile != null ? profile.getFirstName() : "Unknown")
                        .lastName(profile != null ? profile.getLastName() : "Unknown")
                        .phoneNumber(profile != null ? profile.getPhone() : null)
                        .kycStatus(user.getKycStatus() != null ? user.getKycStatus() : KycStatus.NOT_STARTED)
                        .kycVerifiedAt(user.getKycStatus() == KycStatus.VERIFIED ? OffsetDateTime.now() : null)
                        .createdAt(user.getCreatedAt())
                        .build()));
    }

    @Transactional
    public AccountHolder updateProfile(UUID accountHolderId, String firstName, String lastName, String phoneNumber) {
        AccountHolder accountHolder = getById(accountHolderId);
        accountHolder.setFirstName(firstName);
        accountHolder.setLastName(lastName);
        accountHolder.setPhoneNumber(phoneNumber);
        if (accountHolder.getKycStatus() == KycStatus.NOT_STARTED) {
            accountHolder.setKycStatus(KycStatus.PENDING);
        }
        return accountHolderRepository.save(accountHolder);
    }
}
