package com.ccapp.wallet.repository;

import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WalletLedgerRepository {

    private final AccountBalanceSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public Optional<AccountBalanceSnapshot> findByAccountCodeAndCurrency(String accountCode, String currency) {
        return snapshotRepository.findFirstByAccountCodeAndCurrency(
                normalizeAccountCode(accountCode),
                normalizeCurrency(currency)
        );
    }

    @Transactional
    public AccountBalanceSnapshot createBalanceIfMissing(String accountCode, String currency) {
        String normalizedAccountCode = normalizeAccountCode(accountCode);
        String normalizedCurrency = normalizeCurrency(currency);

        return snapshotRepository.findFirstByAccountCodeAndCurrency(normalizedAccountCode, normalizedCurrency)
                .orElseGet(() -> createZeroSnapshot(normalizedAccountCode, normalizedCurrency));
    }

    private AccountBalanceSnapshot createZeroSnapshot(String accountCode, String currency) {
        try {
            return snapshotRepository.save(AccountBalanceSnapshot.builder()
                    .accountCode(accountCode)
                    .currency(currency)
                    .balance(BigDecimal.ZERO)
                    .version(0L)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            return snapshotRepository.findFirstByAccountCodeAndCurrency(accountCode, currency)
                    .orElseThrow(() -> exception);
        }
    }

    private String normalizeAccountCode(String accountCode) {
        return accountCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
