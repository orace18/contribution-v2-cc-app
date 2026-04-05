package com.ccapp.identity.account_holder.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.service.CountryService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountHolderService {

    private final AccountHolderRepository accountHolderRepository;
    private final CountryService countryService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional(readOnly = true)
    public AccountHolder getById(UUID accountHolderId) {
        log.info("Identity account-holder getById accountHolderId={}", accountHolderId);
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID accountHolderId) {
        log.debug("Identity account-holder exists check accountHolderId={}", accountHolderId);
        return accountHolderRepository.existsById(accountHolderId);
    }

    @Transactional
    public AccountHolder ensureFromLegacyUser(User user, UserProfile profile) {
        log.info("Identity account-holder ensureFromLegacyUser userId={} firebaseUid={}", user.getId(), user.getFirebaseUid());
        String fallbackCountryCode = countryService.resolveFallbackCountry().getCode();
        String selectedCountryCode = null;
        String serviceCountryCode = fallbackCountryCode;

        AccountHolder existing = accountHolderRepository.findById(user.getId()).orElse(null);
        if (existing != null) {
            log.debug("Identity account-holder already exists userId={} accountHolderId={}", user.getId(), existing.getId());
            return existing;
        }

        AccountHolder created = accountHolderRepository.save(AccountHolder.builder()
                .id(user.getId())
                .firstName(profile != null ? profile.getFirstName() : "Unknown")
                .lastName(profile != null ? profile.getLastName() : "Unknown")
                .phoneNumber(profile != null ? profile.getPhone() : null)
                .countryCode(selectedCountryCode)
                .serviceCountryCode(serviceCountryCode)
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(user.getKycStatus() != null ? user.getKycStatus() : KycStatus.NOT_STARTED)
                .kycVerifiedAt(user.getKycStatus() == KycStatus.VERIFIED ? OffsetDateTime.now() : null)
                .createdAt(user.getCreatedAt())
                .build());
        log.info("Identity account-holder created from legacy user accountHolderId={} serviceCountryCode={}", created.getId(), created.getServiceCountryCode());
        countryChartOfAccountsService.findOrCreateWallet(created);
        domainEventPublisher.publish(AccountHolderCreatedEvent.create(created.getId()));
        return created;
    }

    @Transactional
    public AccountHolder selectCountry(UUID accountHolderId, String requestedCountryCode) {
        log.info("AccountHolderService selectCountry accountHolderId={} requestedCountryCode={}", accountHolderId, requestedCountryCode);
        AccountHolder accountHolder = getById(accountHolderId);
        String selectedCountryCode = countryService.resolveSelectedCountryOrThrow(requestedCountryCode).getCode();
        String serviceCountryCode = countryService.resolveServiceCountryForSelection(selectedCountryCode).getCode();

        if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().equalsIgnoreCase(selectedCountryCode)) {
            log.warn("AccountHolderService selectCountry denied immutable country accountHolderId={} currentCountry={} requestedCountry={}",
                    accountHolderId, accountHolder.getCountryCode(), selectedCountryCode);
            throw new BusinessException("Account holder country is immutable");
        }

        accountHolder.setCountryCode(selectedCountryCode);
        accountHolder.setServiceCountryCode(serviceCountryCode);
        AccountHolder saved = accountHolderRepository.save(accountHolder);
        log.info("AccountHolderService selectCountry success accountHolderId={} countryCode={} serviceCountryCode={}",
                saved.getId(), saved.getCountryCode(), saved.getServiceCountryCode());
        countryChartOfAccountsService.findOrCreateWallet(saved);
        return saved;
    }

    @Transactional
    public void updateName(UUID accountHolderId, String firstName, String lastName) {
        log.info("Identity account-holder updateName accountHolderId={}", accountHolderId);
        AccountHolder accountHolder = getById(accountHolderId);
        accountHolder.setFirstName(firstName.trim());
        accountHolder.setLastName(lastName.trim());
        accountHolderRepository.save(accountHolder);
    }

    @Transactional
    public AccountHolder updateProfile(UUID accountHolderId,
                                       String firstName,
                                       String lastName,
                                       String phoneNumber,
                                       String requestedCountryCode) {
        log.info("Identity account-holder updateProfile accountHolderId={} requestedCountryCode={}", accountHolderId, requestedCountryCode);
        AccountHolder accountHolder = getById(accountHolderId);
        String selectedCountryCode = countryService.resolveSelectedCountryOrThrow(requestedCountryCode).getCode();
        String serviceCountryCode = countryService.resolveServiceCountryForSelection(selectedCountryCode).getCode();
        String normalizedPhone = phoneNumber == null ? null : phoneNumber.trim();

        if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().equalsIgnoreCase(selectedCountryCode)) {
            log.warn("Identity account-holder updateProfile denied immutable country accountHolderId={} currentCountry={} requestedCountry={}",
                    accountHolderId, accountHolder.getCountryCode(), selectedCountryCode);
            throw new BusinessException("Account holder country is immutable");
        }
        if (normalizedPhone != null
                && !normalizedPhone.isBlank()
                && accountHolderRepository.existsByPhoneNumberAndIdNot(normalizedPhone, accountHolderId)) {
            log.warn("Identity account-holder updateProfile denied phone conflict accountHolderId={} phone={}", accountHolderId, normalizedPhone);
            throw new ConflictException("Phone number already used");
        }

        accountHolder.setFirstName(firstName);
        accountHolder.setLastName(lastName);
        accountHolder.setPhoneNumber(normalizedPhone);
        accountHolder.setCountryCode(selectedCountryCode);
        accountHolder.setServiceCountryCode(serviceCountryCode);
        AccountHolder saved = accountHolderRepository.save(accountHolder);
        log.info("Identity account-holder updateProfile success accountHolderId={} countryCode={} serviceCountryCode={}",
                saved.getId(), saved.getCountryCode(), saved.getServiceCountryCode());
        countryChartOfAccountsService.findOrCreateWallet(saved);
        return saved;
    }
}
