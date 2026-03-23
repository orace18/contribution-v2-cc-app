package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.wallet.dto.WalletBalanceResponse;
import com.ccapp.wallet.repository.WalletReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
@Transactional(readOnly = true)
public class WalletBalanceService {

    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final WalletReservationRepository reservationRepository;
    private final WalletRepository walletRepository;

    public WalletBalanceResponse getBalance(UUID accountHolderId, String currency) {
        Wallet wallet = walletRepository
                .findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("No active wallet found for: " + accountHolderId));

        BigDecimal main = computeMainBalance(wallet, currency);
        BigDecimal reserved = computeReservedBalance(wallet);
        BigDecimal available = main.subtract(reserved);

        log.debug("wallet_balance accountHolderId={} currency={} main={} reserved={} available={}",
                accountHolderId, currency, main, reserved, available);

        return new WalletBalanceResponse(wallet.getId(), accountHolderId, currency, main, reserved, available);
    }

    /** Returns spendable main balance derived from the ledger snapshot. */
    public BigDecimal getMainBalance(Wallet wallet, String currency) {
        return computeMainBalance(wallet, currency);
    }

    /** Returns available balance: main minus all active reservations. */
    public BigDecimal getAvailableBalance(Wallet wallet, String currency) {
        return computeMainBalance(wallet, currency).subtract(computeReservedBalance(wallet));
    }

    // -------------------------------------------------------------------------

    private BigDecimal computeMainBalance(Wallet wallet, String currency) {
        AccountHolder holder = resolveHolder(wallet.getAccountHolderId());
        String walletCode = countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();
        // Negate because CREDIT (incoming funds) yields a negative snapshot value
        return snapshotRepository
                .findByAccountCodeAndCurrency(walletCode, currency)
                .map(s -> s.getBalance().negate())
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal computeReservedBalance(Wallet wallet) {
        return reservationRepository
                .sumActiveReservationsByWalletId(wallet.getId())
                .orElse(BigDecimal.ZERO);
    }

    private AccountHolder resolveHolder(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + accountHolderId));
    }
}
