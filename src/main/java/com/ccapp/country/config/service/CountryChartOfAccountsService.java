package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.dto.CountryChartAccountDetailsResponse;
import com.ccapp.country.config.dto.CountryChartAccountHistoryResponse;
import com.ccapp.country.config.dto.CountryChartAccountMovementResponse;
import com.ccapp.country.config.dto.CountryChartAccountSummaryResponse;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CountryChartOfAccountsService {

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

    private CountryChartAccountSummaryResponse toSummary(CountryChartAccount account, BigDecimal currentBalance) {
        return new CountryChartAccountSummaryResponse(
                account.getId(),
                account.getCountryCode(),
                account.getAccountCode(),
                account.getAccountName(),
                account.getAccountType(),
                account.getCurrency(),
                currentBalance,
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private CountryChartAccountDetailsResponse toDetails(CountryChartAccount account, BigDecimal currentBalance) {
        return new CountryChartAccountDetailsResponse(
                account.getId(),
                account.getCountryCode(),
                account.getAccountCode(),
                account.getAccountName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getOpeningBalance(),
                currentBalance,
                account.getStatus(),
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
