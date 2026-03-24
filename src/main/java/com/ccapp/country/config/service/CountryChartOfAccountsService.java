package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.AccountGroupMaster;
import com.ccapp.country.config.domain.entity.CountryAccountSystemMapping;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryMasterAccountTemplate;
import com.ccapp.country.config.dto.CreateCountryChartAccountRequest;
import com.ccapp.country.config.dto.CountryChartAccountDetailsResponse;
import com.ccapp.country.config.dto.CountryChartAccountHistoryResponse;
import com.ccapp.country.config.dto.CountryChartAccountMovementResponse;
import com.ccapp.country.config.dto.CountryChartAccountSummaryResponse;
import com.ccapp.country.config.repository.AccountGroupMasterRepository;
import com.ccapp.country.config.repository.CountryAccountSystemMappingRepository;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryChartOfAccountsService {

    private static final int GENERATED_SEQUENCE_WIDTH = 6;

    private final CountryRepository countryRepository;
    private final CountryChartAccountRepository countryChartAccountRepository;
    private final AccountGroupMasterRepository accountGroupMasterRepository;
    private final CountryAccountSystemMappingRepository countryAccountSystemMappingRepository;
    private final AccountBalanceSnapshotRepository accountBalanceSnapshotRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public List<CountryChartAccountSummaryResponse> listCountryAccounts(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        ensureCountryChartGenerated(normalizedCountryCode);
        Country country = resolveCountry(normalizedCountryCode);
        String currency = country.getCurrency();

        Map<String, BigDecimal> balancesByAccountCode = accountBalanceSnapshotRepository
                .findByCurrencyOrderByAccountCodeAsc(currency)
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshot.getAccountCode().trim().toUpperCase(Locale.ROOT),
                        snapshot -> snapshot.getBalance() == null ? BigDecimal.ZERO : snapshot.getBalance(),
                        (left, right) -> right
                ));

        return countryChartAccountRepository.findByCountryCodeOrderByAccountCodeAsc(country.getCode())
                .stream()
                .map(account -> toSummary(account, balancesByAccountCode.getOrDefault(account.getAccountCode(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public CountryChartAccountDetailsResponse getCountryAccount(String countryCode, UUID accountId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        ensureCountryChartGenerated(normalizedCountryCode);
        CountryChartAccount account = resolveAccount(normalizedCountryCode, accountId);
        BigDecimal currentBalance = accountBalanceSnapshotRepository
                .findFirstByAccountCodeAndCurrency(account.getAccountCode(), account.getCurrency())
                .map(snapshot -> snapshot.getBalance() == null ? BigDecimal.ZERO : snapshot.getBalance())
                .orElse(BigDecimal.ZERO);
        return toDetails(account, currentBalance);
    }

    @Transactional
    public CountryChartAccountHistoryResponse getCountryAccountHistory(String countryCode, UUID accountId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        ensureCountryChartGenerated(normalizedCountryCode);
        CountryChartAccount account = resolveAccount(normalizedCountryCode, accountId);
        CountryChartAccountDetailsResponse details = getCountryAccount(normalizedCountryCode, accountId);

        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountCodeAndCurrencyOrderByCreatedAtAsc(
                account.getAccountCode(),
                account.getCurrency()
        );
        if (entries.isEmpty()) {
            entries = ledgerEntryRepository.findByAccountCodeOrderByCreatedAtAsc(account.getAccountCode());
        }
        List<CountryChartAccountMovementResponse> movements = entries.stream()
                .map(this::toMovement)
                .toList();
        return new CountryChartAccountHistoryResponse(details, movements);
    }

    @Transactional
    public CountryChartAccountDetailsResponse createCountryAccount(String countryCode, CreateCountryChartAccountRequest request) {
        log.warn("country_chart_manual_creation_blocked countryCode={}", normalizeCountryCode(countryCode));
        throw new BusinessException("Manual country account creation is disabled. Accounts are generated automatically from the master template.");
    }

    @Transactional
    public List<CountryChartAccountSummaryResponse> autoGenerateInitializationAccounts(String countryCode) {
        ensureCountryChartGenerated(countryCode);
        return listCountryAccounts(countryCode);
    }

    @Transactional
    public List<CountryChartAccountSummaryResponse> autoGenerateRequiredAccounts(String countryCode) {
        ensureCountryChartGenerated(countryCode);
        return listCountryAccounts(countryCode);
    }

    @Transactional
    public void ensureCountryChartGenerated(String countryCode) {
        Country country = resolveCountry(countryCode);
        bootstrapMasterTemplateIfMissing();

        List<AccountGroupMaster> masters = accountGroupMasterRepository.findAllByOrderByGroupIdAsc();
        if (masters.isEmpty()) {
            throw new BusinessException("Master chart of accounts template is not configured");
        }

        for (AccountGroupMaster master : masters) {
            ensureMasterAccount(country, master);
        }
        for (CountryMasterAccountTemplate template : CountryMasterAccountTemplate.requiredTemplates()) {
            ensureSystemMapping(country, template);
        }
        assertCriticalSystemMappingsExist(country.getCode());
        log.info("country_chart_auto_generated countryCode={} masterGroups={} requiredMappings={}",
                country.getCode(), masters.size(), CountryAccountSystemTag.requiredTags().size());
    }

    @Transactional(readOnly = true)
    public boolean hasRequiredSystemMappings(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        return CountryAccountSystemTag.requiredTags().stream()
                .allMatch(tag -> countryAccountSystemMappingRepository.findByCountryCodeAndSystemTag(normalizedCountryCode, tag)
                        .flatMap(mapping -> countryChartAccountRepository.findByIdAndCountryCode(mapping.getAccountId(), normalizedCountryCode))
                        .isPresent());
    }

    @Transactional(readOnly = true)
    public void assertCriticalSystemMappingsExist(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        for (CountryAccountSystemTag tag : CountryAccountSystemTag.requiredTags()) {
            countryAccountSystemMappingRepository.findByCountryCodeAndSystemTag(normalizedCountryCode, tag)
                    .flatMap(mapping -> countryChartAccountRepository.findByIdAndCountryCode(mapping.getAccountId(), normalizedCountryCode))
                    .orElseThrow(() -> new BusinessException("Critical system account missing for tag " + tag.name()));
        }
    }

    @Transactional
    public CountryChartAccount resolveSystemAccount(String countryCode, CountryAccountSystemTag systemTag) {
        if (systemTag == null) {
            throw new BusinessException("System tag is required");
        }
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        ensureCountryChartGenerated(normalizedCountryCode);
        CountryAccountSystemMapping mapping = countryAccountSystemMappingRepository
                .findByCountryCodeAndSystemTag(normalizedCountryCode, systemTag)
                .orElseThrow(() -> new BusinessException("System mapping not found for tag " + systemTag.name()));
        return countryChartAccountRepository.findByIdAndCountryCode(mapping.getAccountId(), normalizedCountryCode)
                .orElseThrow(() -> new BusinessException("Mapped account not found for tag " + systemTag.name()));
    }

    @Transactional
    public String resolveSystemAccountCode(String countryCode, CountryAccountSystemTag systemTag) {
        return resolveSystemAccount(countryCode, systemTag).getAccountCode();
    }

    @Transactional
    public CountryChartAccount findOrCreate(String countryCode, CountryAccountGroup group) {
        return findOrCreate(countryCode, group, null);
    }

    @Transactional
    public CountryChartAccount findOrCreate(String countryCode, CountryAccountGroup group, String preferredLabel) {
        if (group == null) {
            throw new BusinessException("Account group is required");
        }
        if (group == CountryAccountGroup.MEMBERSHIP_FEES
                || group == CountryAccountGroup.GUARANTEE_FUND
                || group == CountryAccountGroup.ADMINISTRATION
                || group == CountryAccountGroup.SOCIAL_FUND) {
            return resolveOrCreateSharedGroupAccount(countryCode, group, preferredLabel);
        }
        ensureCountryChartGenerated(countryCode);
        CountryMasterAccountTemplate template = resolveTemplate(group);
        if (template.systemTag() != null) {
            return resolveSystemAccount(countryCode, template.systemTag());
        }
        return resolveMasterGroupAccount(countryCode, template.groupId());
    }

    /**
     * Returns the unique USER_WALLET ledger account for this account holder,
     * creating it on-the-fly if it does not yet exist.
     */
    @Transactional
    public CountryChartAccount findOrCreateWallet(AccountHolder accountHolder) {
        if (accountHolder == null) {
            throw new BusinessException("Account holder is required to resolve wallet");
        }
        String countryCode = resolveAccountHolderCountryCode(accountHolder);
        ensureCountryChartGenerated(countryCode);
        Country country = resolveCountry(countryCode);

        return countryChartAccountRepository
                .findByAccountHolderIdAndCountryCodeAndStatus(
                        accountHolder.getId(),
                        country.getCode(),
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseGet(() -> createWalletAccount(country, accountHolder));
    }

    @Transactional(readOnly = true)
    public CountryChartAccount resolveMasterGroupAccount(String countryCode, int groupId) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        CountryMasterAccountTemplate template = CountryMasterAccountTemplate.fromGroupId(groupId);
        return countryChartAccountRepository
                .findFirstByCountryCodeAndMasterGroupIdAndAccountHolderIdIsNullAndStatusOrderByCreatedAtAsc(
                        normalizedCountryCode,
                        template.groupId(),
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException("Country master account not found for group " + groupId));
    }

    private void bootstrapMasterTemplateIfMissing() {
        List<AccountGroupMaster> toSave = Arrays.stream(CountryMasterAccountTemplate.values())
                .filter(template -> accountGroupMasterRepository.findById(template.groupId()).isEmpty())
                .map(template -> AccountGroupMaster.builder()
                        .groupId(template.groupId())
                        .name(template.displayName())
                        .type(template.accountType())
                        .description(template.description())
                        .systemRequired(template.systemRequired())
                        .build())
                .toList();
        if (!toSave.isEmpty()) {
            accountGroupMasterRepository.saveAll(toSave);
            log.info("account_groups_master_seeded count={}", toSave.size());
        }
    }

    private CountryChartAccount ensureMasterAccount(Country country, AccountGroupMaster master) {
        return countryChartAccountRepository
                .findFirstByCountryCodeAndMasterGroupIdAndAccountHolderIdIsNullAndStatusOrderByCreatedAtAsc(
                        country.getCode(),
                        master.getGroupId(),
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseGet(() -> createMasterAccount(country, master));
    }

    private CountryChartAccount createMasterAccount(Country country, AccountGroupMaster master) {
        CountryMasterAccountTemplate template = CountryMasterAccountTemplate.fromGroupId(master.getGroupId());
        String accountNumber = generateAccountNumber(country.getCode(), template.accountNumberPrefix());
        CountryChartAccount candidate = CountryChartAccount.builder()
                .countryCode(country.getCode().trim().toUpperCase(Locale.ROOT))
                .accountCode(accountNumber)
                .accountNumber(accountNumber)
                .accountName(normalizeLabel(master.getName(), template.displayName()))
                .accountGroup(template.accountGroup())
                .masterGroupId(template.groupId())
                .systemTag(template.systemTag())
                .autoGenerated(true)
                .accountType(master.getType() == null ? template.accountType() : master.getType())
                .currency(country.getCurrency())
                .openingBalance(BigDecimal.ZERO)
                .status(CountryChartAccountStatus.ACTIVE)
                .build();
        try {
            CountryChartAccount saved = countryChartAccountRepository.save(candidate);
            log.info("country_master_account_created countryCode={} groupId={} accountCode={} systemTag={}",
                    country.getCode(), master.getGroupId(), saved.getAccountCode(), template.systemTag());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            return countryChartAccountRepository
                    .findFirstByCountryCodeAndMasterGroupIdAndAccountHolderIdIsNullAndStatusOrderByCreatedAtAsc(
                            country.getCode(),
                            master.getGroupId(),
                            CountryChartAccountStatus.ACTIVE
                    )
                    .orElseThrow(() -> ex);
        }
    }

    private CountryChartAccount ensureSystemMapping(Country country, CountryMasterAccountTemplate template) {
        CountryChartAccount account = countryChartAccountRepository
                .findFirstByCountryCodeAndSystemTagAndStatusOrderByCreatedAtAsc(
                        country.getCode(),
                        template.systemTag(),
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseGet(() -> attachSystemTag(country, template));

        boolean mappingNeedsUpdate = countryAccountSystemMappingRepository
                .findByCountryCodeAndSystemTag(country.getCode(), template.systemTag())
                .map(existing -> !account.getId().equals(existing.getAccountId()))
                .orElse(true);
        if (mappingNeedsUpdate) {
            countryAccountSystemMappingRepository.save(CountryAccountSystemMapping.builder()
                    .countryCode(country.getCode())
                    .systemTag(template.systemTag())
                    .accountId(account.getId())
                    .build());
        }
        return account;
    }

    private CountryChartAccount attachSystemTag(Country country, CountryMasterAccountTemplate template) {
        AccountGroupMaster master = accountGroupMasterRepository.findById(template.groupId())
                .orElseThrow(() -> new BusinessException("Master account group not found for " + template.groupId()));
        CountryChartAccount account = ensureMasterAccount(country, master);
        if (account.getSystemTag() == template.systemTag()) {
            return account;
        }
        account.setSystemTag(template.systemTag());
        return countryChartAccountRepository.save(account);
    }

    private CountryChartAccount createWalletAccount(Country country, AccountHolder accountHolder) {
        String accountNumber = generateAccountNumber(country.getCode(), CountryMasterAccountTemplate.MEMBERS_WALLET.accountNumberPrefix());
        String label = "Wallet " + accountHolder.getFirstName() + " " + accountHolder.getLastName();
        CountryChartAccount candidate = CountryChartAccount.builder()
                .countryCode(country.getCode().trim().toUpperCase(Locale.ROOT))
                .accountCode(accountNumber)
                .accountNumber(accountNumber)
                .accountName(normalizeLabel(label, CountryAccountGroup.USER_WALLET.defaultLabel()))
                .accountGroup(CountryAccountGroup.USER_WALLET)
                .masterGroupId(CountryMasterAccountTemplate.MEMBERS_WALLET.groupId())
                .autoGenerated(true)
                .accountType(CountryAccountGroup.USER_WALLET.defaultAccountType())
                .currency(country.getCurrency())
                .openingBalance(BigDecimal.ZERO)
                .status(CountryChartAccountStatus.ACTIVE)
                .accountHolderId(accountHolder.getId())
                .build();
        try {
            CountryChartAccount saved = countryChartAccountRepository.save(candidate);
            log.info("wallet_account_created accountHolderId={} countryCode={} accountCode={}",
                    accountHolder.getId(), country.getCode(), saved.getAccountCode());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            return countryChartAccountRepository
                    .findByAccountHolderIdAndCountryCodeAndStatus(
                            accountHolder.getId(),
                            country.getCode(),
                            CountryChartAccountStatus.ACTIVE
                    )
                    .orElseThrow(() -> ex);
        }
    }

    private CountryChartAccount resolveOrCreateSharedGroupAccount(String countryCode,
                                                                  CountryAccountGroup group,
                                                                  String preferredLabel) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        ensureCountryChartGenerated(normalizedCountryCode);
        Country country = resolveCountry(normalizedCountryCode);
        return countryChartAccountRepository
                .findFirstByCountryCodeAndAccountGroupAndStatusOrderByCreatedAtAsc(
                        normalizedCountryCode,
                        group,
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseGet(() -> createSharedGroupAccount(country, group, preferredLabel));
    }

    private CountryChartAccount createSharedGroupAccount(Country country,
                                                         CountryAccountGroup group,
                                                         String preferredLabel) {
        String accountNumber = CountryAccountGroup.MEMBERSHIP_FEES == group
                ? "750001"
                : generateAccountNumber(country.getCode(), group.prefix());
        CountryChartAccount candidate = CountryChartAccount.builder()
                .countryCode(country.getCode().trim().toUpperCase(Locale.ROOT))
                .accountCode(accountNumber)
                .accountNumber(accountNumber)
                .accountName(normalizeLabel(preferredLabel, group.defaultLabel()))
                .accountGroup(group)
                .masterGroupId(null)
                .systemTag(null)
                .autoGenerated(true)
                .accountType(group.defaultAccountType())
                .currency(country.getCurrency())
                .openingBalance(BigDecimal.ZERO)
                .status(CountryChartAccountStatus.ACTIVE)
                .accountHolderId(null)
                .build();
        try {
            return countryChartAccountRepository.save(candidate);
        } catch (DataIntegrityViolationException ex) {
            return countryChartAccountRepository
                    .findFirstByCountryCodeAndAccountGroupAndStatusOrderByCreatedAtAsc(
                            country.getCode(),
                            group,
                            CountryChartAccountStatus.ACTIVE
                    )
                    .orElseThrow(() -> ex);
        }
    }

    private CountryMasterAccountTemplate resolveTemplate(CountryAccountGroup group) {
        return switch (group) {
            case CAPITAL -> CountryMasterAccountTemplate.CAPITAL;
            case FUND_STATUTAIRE, GUARANTEE_FUND -> CountryMasterAccountTemplate.FUND_STATUTAIRE;
            case USER_WALLET -> CountryMasterAccountTemplate.MEMBERS_WALLET;
            case PSP_CLEARING -> CountryMasterAccountTemplate.PSP_CLEARING;
            case BANKS -> CountryMasterAccountTemplate.BANKS;
            case CASH -> CountryMasterAccountTemplate.CASH;
            case MARKETPLACE_POOL -> CountryMasterAccountTemplate.MARKETPLACE_POOL;
            case MLM_POOL, COMMISSION_POOL -> CountryMasterAccountTemplate.MLM_POOL;
            case SALES, MEMBERSHIP_FEES -> CountryMasterAccountTemplate.SALES;
            case LOAN_PORTFOLIO, PARTNER_SETTLEMENT -> CountryMasterAccountTemplate.LOAN_PORTFOLIO;
            case FINTECH_REVENUE, PLATFORM_REVENUE, ADMINISTRATION -> CountryMasterAccountTemplate.FINTECH_REVENUE;
            case INTEREST_REVENUE -> CountryMasterAccountTemplate.INTEREST_REVENUE;
            case SOCIAL_FUND -> CountryMasterAccountTemplate.FUND_STATUTAIRE;
        };
    }

    private String resolveAccountHolderCountryCode(AccountHolder accountHolder) {
        if (accountHolder.getServiceCountryCode() != null && !accountHolder.getServiceCountryCode().isBlank()) {
            return accountHolder.getServiceCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().isBlank()) {
            return accountHolder.getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        return countryRepository.findByFallbackTrue()
                .map(Country::getCode)
                .orElse("INT");
    }

    private Country resolveCountry(String countryCode) {
        return countryRepository.findByCode(normalizeCountryCode(countryCode))
                .orElseThrow(() -> new BusinessException("Country not found"));
    }

    private CountryChartAccount resolveAccount(String countryCode, UUID accountId) {
        if (accountId == null) {
            throw new BusinessException("Account id is required");
        }
        return countryChartAccountRepository.findByIdAndCountryCode(accountId, normalizeCountryCode(countryCode))
                .orElseThrow(() -> new BusinessException("Country account not found"));
    }

    private String generateAccountNumber(String countryCode, String prefix) {
        int totalLength = prefix.length() + GENERATED_SEQUENCE_WIDTH;
        int sequence = countryChartAccountRepository.findMaxGeneratedSequenceForCountryAndPrefix(
                normalizeCountryCode(countryCode),
                prefix,
                totalLength
        ) + 1;
        if (sequence > 999_999) {
            throw new BusinessException("Account number sequence exhausted for group " + prefix);
        }
        return prefix + String.format(Locale.ROOT, "%0" + GENERATED_SEQUENCE_WIDTH + "d", sequence);
    }

    private String normalizeLabel(String rawLabel, String defaultLabel) {
        String label = (rawLabel == null || rawLabel.isBlank()) ? defaultLabel : rawLabel.trim();
        if (label.length() > 180) {
            return label.substring(0, 180);
        }
        return label;
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private CountryChartAccountSummaryResponse toSummary(CountryChartAccount account, BigDecimal currentBalance) {
        return new CountryChartAccountSummaryResponse(
                account.getId(),
                account.getCountryCode(),
                account.getAccountCode(),
                account.getAccountNumber(),
                account.getAccountName(),
                account.getAccountName(),
                account.getAccountGroup(),
                account.getAccountType(),
                account.getCurrency(),
                currentBalance,
                account.getStatus(),
                account.getAccountHolderId(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private CountryChartAccountDetailsResponse toDetails(CountryChartAccount account, BigDecimal currentBalance) {
        return new CountryChartAccountDetailsResponse(
                account.getId(),
                account.getCountryCode(),
                account.getAccountCode(),
                account.getAccountNumber(),
                account.getAccountName(),
                account.getAccountName(),
                account.getAccountGroup(),
                account.getAccountType(),
                account.getCurrency(),
                account.getOpeningBalance(),
                currentBalance,
                account.getStatus(),
                account.getAccountHolderId(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private CountryChartAccountMovementResponse toMovement(LedgerEntry entry) {
        return new CountryChartAccountMovementResponse(
                entry.getId(),
                entry.getEvent() == null ? null : entry.getEvent().getId(),
                entry.getLineNo(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getDescription(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
