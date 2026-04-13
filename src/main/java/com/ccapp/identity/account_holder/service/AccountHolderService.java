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
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountHolderService {

    private static final String PROVISIONAL_COUNTRY_CODE = "INT";

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
                .kycLevel(user.getKycStatus() == KycStatus.VERIFIED ? KycLevel.LEVEL_1_BASIC : KycLevel.LEVEL_0_UNVERIFIED)
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
        String currentCountryCode = normalizeCountryCode(accountHolder.getCountryCode());

        if (isImmutableCountrySelection(currentCountryCode, selectedCountryCode)) {
            log.warn("AccountHolderService selectCountry denied immutable country accountHolderId={} currentCountry={} requestedCountry={}",
                    accountHolderId, currentCountryCode, selectedCountryCode);
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

    /**
     * Records the civil identity declared by the user before KYC document upload.
     * Stores firstName, lastName, birthDate, nationality, declaredDocumentType and
     * documentNumber so the back-office can compare declared vs document identity.
     */
    @Transactional
    public void declareIdentity(UUID accountHolderId,
                                String firstName,
                                String lastName,
                                LocalDate birthDate,
                                String nationality,
                                String documentType,
                                String documentNumber) {
        log.info("AccountHolderService declareIdentity accountHolderId={} documentType={}", accountHolderId, documentType);
        AccountHolder accountHolder = getById(accountHolderId);
        accountHolder.setFirstName(normalizeName(firstName));
        accountHolder.setLastName(normalizeName(lastName));
        accountHolder.setBirthDate(birthDate);
        accountHolder.setNationality(nationality == null ? null : nationality.trim().toUpperCase(Locale.ROOT));
        accountHolder.setDeclaredDocumentType(documentType == null ? null : documentType.trim().toUpperCase(Locale.ROOT));
        accountHolder.setDocumentNumber(documentNumber == null ? null : documentNumber.trim());
        accountHolderRepository.save(accountHolder);
        log.info("AccountHolderService declareIdentity saved accountHolderId={}", accountHolderId);
    }

    @Transactional
    public void updateName(UUID accountHolderId, String firstName, String lastName) {
        log.info("Identity account-holder updateName accountHolderId={}", accountHolderId);
        AccountHolder accountHolder = getById(accountHolderId);
        accountHolder.setFirstName(normalizeName(firstName));
        accountHolder.setLastName(normalizeName(lastName));
        accountHolderRepository.save(accountHolder);
    }

    @Transactional
    public AccountHolder createManagedAccount(UUID managerUserId,
                                              String firstName,
                                              String lastName,
                                              String phoneNumber,
                                              String requestedCountryCode) {
        log.info("Identity account-holder createManagedAccount managerUserId={} requestedCountryCode={}",
                managerUserId, requestedCountryCode);
        String selectedCountryCode = countryService.resolveSelectedCountryOrThrow(requestedCountryCode).getCode();
        String serviceCountryCode = countryService.resolveServiceCountryForSelection(selectedCountryCode).getCode();
        String normalizedPhone = normalizePhone(phoneNumber);

        if (accountHolderRepository.existsByPhoneNumber(normalizedPhone)) {
            log.warn("Identity account-holder createManagedAccount denied phone conflict managerUserId={} phone={}",
                    managerUserId, normalizedPhone);
            throw new ConflictException("Phone number already used");
        }

        AccountHolder created = accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(normalizeName(firstName))
                .lastName(normalizeName(lastName))
                .phoneNumber(normalizedPhone)
                .countryCode(selectedCountryCode)
                .serviceCountryCode(serviceCountryCode)
                .accountType(AccountType.ASSISTED)
                .createdByUserId(managerUserId)
                .createdVia(AccountCreatedVia.MANAGED_ACCOUNT)
                .kycStatus(KycStatus.NOT_STARTED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .build());
        log.info("Identity account-holder createManagedAccount success accountHolderId={} serviceCountryCode={} createdByUserId={}",
                created.getId(), created.getServiceCountryCode(), managerUserId);
        countryChartOfAccountsService.findOrCreateWallet(created);
        domainEventPublisher.publish(AccountHolderCreatedEvent.create(created.getId()));
        return created;
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
        String currentCountryCode = normalizeCountryCode(accountHolder.getCountryCode());

        if (isImmutableCountrySelection(currentCountryCode, selectedCountryCode)) {
            log.warn("Identity account-holder updateProfile denied immutable country accountHolderId={} currentCountry={} requestedCountry={}",
                    accountHolderId, currentCountryCode, selectedCountryCode);
            throw new BusinessException("Account holder country is immutable");
        }
        if (normalizedPhone != null
                && !normalizedPhone.isBlank()
                && accountHolderRepository.existsByPhoneNumberAndIdNot(normalizedPhone, accountHolderId)) {
            log.warn("Identity account-holder updateProfile denied phone conflict accountHolderId={} phone={}", accountHolderId, normalizedPhone);
            throw new ConflictException("Phone number already used");
        }

        accountHolder.setFirstName(normalizeName(firstName));
        accountHolder.setLastName(normalizeName(lastName));
        accountHolder.setPhoneNumber(normalizedPhone);
        accountHolder.setCountryCode(selectedCountryCode);
        accountHolder.setServiceCountryCode(serviceCountryCode);
        AccountHolder saved = accountHolderRepository.save(accountHolder);
        log.info("Identity account-holder updateProfile success accountHolderId={} countryCode={} serviceCountryCode={}",
                saved.getId(), saved.getCountryCode(), saved.getServiceCountryCode());
        countryChartOfAccountsService.findOrCreateWallet(saved);
        return saved;
    }

    private String normalizeName(String value) {
        if (value == null) {
            throw new BusinessException("Name is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("Name is required");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        if (value == null) {
            throw new BusinessException("Phone number is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("Phone number is required");
        }
        return normalized;
    }

    private boolean isImmutableCountrySelection(String currentCountryCode, String selectedCountryCode) {
        if (currentCountryCode == null || currentCountryCode.equals(selectedCountryCode)) {
            return false;
        }
        return !PROVISIONAL_COUNTRY_CODE.equals(currentCountryCode);
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
