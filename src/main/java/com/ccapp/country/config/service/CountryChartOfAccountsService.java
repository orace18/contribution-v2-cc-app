package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.dto.CreateCountryChartAccountRequest;
import com.ccapp.country.config.dto.CountryChartAccountDetailsResponse;
import com.ccapp.country.config.dto.CountryChartAccountHistoryResponse;
import com.ccapp.country.config.dto.CountryChartAccountMovementResponse;
import com.ccapp.country.config.dto.CountryChartAccountSummaryResponse;
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
    private final AccountBalanceSnapshotRepository accountBalanceSnapshotRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public List<CountryChartAccountSummaryResponse> listCountryAccounts(String countryCode) {
        Country country = resolveCountry(countryCode);
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

    @Transactional(readOnly = true)
    public CountryChartAccountDetailsResponse getCountryAccount(String countryCode, UUID accountId) {
        Country country = resolveCountry(countryCode);
        CountryChartAccount account = resolveAccount(country.getCode(), accountId);
        BigDecimal currentBalance = accountBalanceSnapshotRepository
                .findFirstByAccountCodeAndCurrency(account.getAccountCode(), account.getCurrency())
                .map(snapshot -> snapshot.getBalance() == null ? BigDecimal.ZERO : snapshot.getBalance())
                .orElse(BigDecimal.ZERO);
        return toDetails(account, currentBalance);
    }

    @Transactional(readOnly = true)
    public CountryChartAccountHistoryResponse getCountryAccountHistory(String countryCode, UUID accountId) {
        Country country = resolveCountry(countryCode);
        CountryChartAccount account = resolveAccount(country.getCode(), accountId);
        CountryChartAccountDetailsResponse details = getCountryAccount(countryCode, accountId);

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
        if (request == null) {
            throw new BusinessException("Request body is required");
        }
        if (CountryAccountGroup.USER_WALLET == request.group()) {
            throw new BusinessException("USER_WALLET accounts are auto-managed per account holder and cannot be created manually");
        }
        CountryChartAccount account = findOrCreate(countryCode, request.group(), request.label());
        BigDecimal currentBalance = accountBalanceSnapshotRepository
                .findFirstByAccountCodeAndCurrency(account.getAccountCode(), account.getCurrency())
                .map(snapshot -> snapshot.getBalance() == null ? BigDecimal.ZERO : snapshot.getBalance())
                .orElse(BigDecimal.ZERO);
        return toDetails(account, currentBalance);
    }

    @Transactional
    public List<CountryChartAccountSummaryResponse> autoGenerateInitializationAccounts(String countryCode) {
        Country country = resolveCountry(countryCode);
        for (CountryAccountGroup group : CountryAccountGroup.defaultInitializationGroups()) {
            findOrCreate(country.getCode(), group);
        }
        return listCountryAccounts(country.getCode());
    }

    @Transactional
    public List<CountryChartAccountSummaryResponse> autoGenerateRequiredAccounts(String countryCode) {
        Country country = resolveCountry(countryCode);
        for (CountryAccountGroup group : CountryAccountGroup.requiredGroups()) {
            findOrCreate(country.getCode(), group);
        }
        return listCountryAccounts(country.getCode());
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
        Country country = resolveCountry(countryCode);
        return countryChartAccountRepository
                .findFirstByCountryCodeAndAccountGroupAndStatusOrderByCreatedAtAsc(
                        country.getCode(),
                        group,
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseGet(() -> createNewGroupAccount(country, group, preferredLabel));
    }

    /**
     * Returns the unique USER_WALLET ledger account for this account holder,
     * creating it on-the-fly if it does not yet exist.
     * <p>
     * This is the ONLY safe way to access a holder's wallet in financial flows.
     * Never call findOrCreate(country, USER_WALLET) for individual wallets —
     * that would return any first wallet, not the holder's own.
     */
    @Transactional
    public CountryChartAccount findOrCreateWallet(AccountHolder accountHolder) {
        if (accountHolder == null) {
            throw new BusinessException("Account holder is required to resolve wallet");
        }
        String countryCode = resolveAccountHolderCountryCode(accountHolder);
        Country country = resolveCountry(countryCode);

        return countryChartAccountRepository
                .findByAccountHolderIdAndCountryCodeAndStatus(
                        accountHolder.getId(),
                        country.getCode(),
                        CountryChartAccountStatus.ACTIVE
                )
                .orElseGet(() -> createWalletAccount(country, accountHolder));
    }

    private CountryChartAccount createWalletAccount(Country country, AccountHolder accountHolder) {
        String accountNumber = generateAccountNumber(CountryAccountGroup.USER_WALLET);
        String label = "Wallet " + accountHolder.getFirstName() + " " + accountHolder.getLastName();
        CountryChartAccount candidate = CountryChartAccount.builder()
                .countryCode(country.getCode().trim().toUpperCase(Locale.ROOT))
                .accountCode(accountNumber)
                .accountNumber(accountNumber)
                .accountName(normalizeLabel(label, CountryAccountGroup.USER_WALLET.defaultLabel()))
                .accountGroup(CountryAccountGroup.USER_WALLET)
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
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        String normalizedCode = countryCode.trim().toUpperCase(Locale.ROOT);
        return countryRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
    }

    private CountryChartAccount resolveAccount(String countryCode, UUID accountId) {
        if (accountId == null) {
            throw new BusinessException("Account id is required");
        }
        return countryChartAccountRepository.findByIdAndCountryCode(accountId, countryCode)
                .orElseThrow(() -> new BusinessException("Country account not found"));
    }

    private CountryChartAccount createNewGroupAccount(Country country, CountryAccountGroup group, String preferredLabel) {
        String normalizedCountryCode = country.getCode().trim().toUpperCase(Locale.ROOT);
        String accountNumber = generateAccountNumber(group);
        String label = normalizeLabel(preferredLabel, group.defaultLabel());
        CountryChartAccount candidate = CountryChartAccount.builder()
                .countryCode(normalizedCountryCode)
                .accountCode(accountNumber)
                .accountNumber(accountNumber)
                .accountName(label)
                .accountGroup(group)
                .accountType(group.defaultAccountType())
                .currency(country.getCurrency())
                .openingBalance(BigDecimal.ZERO)
                .status(CountryChartAccountStatus.ACTIVE)
                .build();
        try {
            return countryChartAccountRepository.save(candidate);
        } catch (DataIntegrityViolationException ex) {
            return countryChartAccountRepository
                    .findFirstByCountryCodeAndAccountGroupAndStatusOrderByCreatedAtAsc(
                            normalizedCountryCode,
                            group,
                            CountryChartAccountStatus.ACTIVE
                    )
                    .orElseThrow(() -> ex);
        }
    }

    private String generateAccountNumber(CountryAccountGroup group) {
        String prefix = group.prefix();
        int totalLength = prefix.length() + GENERATED_SEQUENCE_WIDTH;
        int sequence = countryChartAccountRepository.findMaxGeneratedSequenceForPrefix(prefix, totalLength) + 1;
        if (sequence > 999_999) {
            throw new BusinessException("Account number sequence exhausted for group " + group.name());
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
                entry.getEvent().getId(),
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
