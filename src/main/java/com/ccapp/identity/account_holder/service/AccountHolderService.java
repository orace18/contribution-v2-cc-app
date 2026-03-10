package com.ccapp.identity.account_holder.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.service.CountryService;
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
    private final CountryService countryService;

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
        String fallbackCountryCode = countryService.resolveFallbackCountry().getCode();
        String selectedCountryCode = null;
        String serviceCountryCode = fallbackCountryCode;

        AccountHolder existing = accountHolderRepository.findById(user.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        return accountHolderRepository.save(AccountHolder.builder()
                .id(user.getId())
                .firstName(profile != null ? profile.getFirstName() : "Unknown")
                .lastName(profile != null ? profile.getLastName() : "Unknown")
                .phoneNumber(profile != null ? profile.getPhone() : null)
                .countryCode(selectedCountryCode)
                .serviceCountryCode(serviceCountryCode)
                .kycStatus(user.getKycStatus() != null ? user.getKycStatus() : KycStatus.NOT_STARTED)
                .kycVerifiedAt(user.getKycStatus() == KycStatus.VERIFIED ? OffsetDateTime.now() : null)
                .createdAt(user.getCreatedAt())
                .build());
    }

    @Transactional
    public AccountHolder updateProfile(UUID accountHolderId,
                                       String firstName,
                                       String lastName,
                                       String phoneNumber,
                                       String requestedCountryCode) {
        AccountHolder accountHolder = getById(accountHolderId);
        String selectedCountryCode = countryService.resolveSelectedCountryOrThrow(requestedCountryCode).getCode();
        String serviceCountryCode = countryService.resolveServiceCountryForSelection(selectedCountryCode).getCode();

        if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().equalsIgnoreCase(selectedCountryCode)) {
            throw new BusinessException("Account holder country is immutable");
        }

        accountHolder.setFirstName(firstName);
        accountHolder.setLastName(lastName);
        accountHolder.setPhoneNumber(phoneNumber);
        accountHolder.setCountryCode(selectedCountryCode);
        accountHolder.setServiceCountryCode(serviceCountryCode);
        if (accountHolder.getKycStatus() == KycStatus.NOT_STARTED) {
            accountHolder.setKycStatus(KycStatus.PENDING);
        }
        return accountHolderRepository.save(accountHolder);
    }
}
