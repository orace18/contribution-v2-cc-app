package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.wallet.repository.WalletLedgerRepository;
import com.ccapp.wallet.dto.WalletBalanceResponse;
import com.ccapp.wallet.repository.WalletReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Derives wallet balance from the ledger (never stores it directly).
 *
 * <p>Balance convention: the ledger snapshot for a USER_WALLET account
 * stores {@code SUM(DEBIT) - SUM(CREDIT)}. Since credits represent incoming
 * funds, the spendable balance is {@code -snapshot.balance}.
 *
 * <pre>
 *   main_balance      = -snapshot.balance
 *   reserved_balance  = SUM(ACTIVE reservations)
 *   available_balance = main_balance - reserved_balance
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletBalanceService {

    private final WalletLedgerRepository walletLedgerRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final WalletReservationRepository reservationRepository;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public Optional<Wallet> findActiveWallet(UUID accountHolderId) {
        return walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE");
    }

    @Transactional
    public WalletBalanceResponse getBalance(UUID accountHolderId, String currency) {
        String normalizedCurrency = normalizeCurrency(currency);
        Wallet wallet = walletRepository
                .findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElse(null);
        if (wallet == null) {
            return new WalletBalanceResponse(
                    null,
                    accountHolderId,
                    normalizedCurrency,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        BigDecimal main = computeMainBalance(wallet, normalizedCurrency);
        BigDecimal reserved = computeReservedBalance(wallet);
        BigDecimal available = main.subtract(reserved);

        log.debug("wallet_balance accountHolderId={} currency={} main={} reserved={} available={}",
                accountHolderId, normalizedCurrency, main, reserved, available);

        return new WalletBalanceResponse(wallet.getId(), accountHolderId, normalizedCurrency, main, reserved, available);
    }

    /** Returns spendable main balance derived from the ledger snapshot. */
    @Transactional
    public BigDecimal getMainBalance(Wallet wallet, String currency) {
        return computeMainBalance(wallet, currency);
    }

    /** Returns available balance: main minus all active reservations. */
    @Transactional
    public BigDecimal getAvailableBalance(Wallet wallet, String currency) {
        return computeMainBalance(wallet, currency).subtract(computeReservedBalance(wallet));
    }

    /** Returns the total amount currently blocked by active reservations. */
    @Transactional(readOnly = true)
    public BigDecimal getReservedBalance(Wallet wallet) {
        return computeReservedBalance(wallet);
    }

    // -------------------------------------------------------------------------

    private BigDecimal computeMainBalance(Wallet wallet, String currency) {
        AccountHolder holder = resolveHolder(wallet.getAccountHolderId());
        String walletCode = countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();
        String normalizedCurrency = normalizeCurrency(currency);
        // Negate because CREDIT (incoming funds) yields a negative snapshot value
        return walletLedgerRepository
                .createBalanceIfMissing(walletCode, normalizedCurrency)
                .getBalance()
                .negate();
    }

    private BigDecimal computeReservedBalance(Wallet wallet) {
        return reservationRepository
                .sumSpendBlockingReservationsByWalletId(wallet.getId(), OffsetDateTime.now())
                .orElse(BigDecimal.ZERO);
    }

    private AccountHolder resolveHolder(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + accountHolderId));
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
