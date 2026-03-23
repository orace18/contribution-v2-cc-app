package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Central permission gateway – MUST be called before any debit.
 *
 * <p>Checks:
 * <ol>
 *   <li>Wallet exists and is ACTIVE</li>
 *   <li>available_balance >= requested amount</li>
 * </ol>
 * Throws {@link BusinessException} if any check fails, blocking the operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletGuardService {

    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;

    /** Returns the ACTIVE wallet or throws. */
    @Transactional(readOnly = true)
    public Wallet requireActiveWallet(UUID accountHolderId) {
        return walletRepository
                .findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException(
                        "No active wallet found for account holder: " + accountHolderId));
    }

    /**
     * Validates that the wallet is ACTIVE and has sufficient available balance.
     *
     * @throws BusinessException immediately if any constraint is violated
     */
    @Transactional(readOnly = true)
    public void checkDebitAllowed(Wallet wallet, BigDecimal amount, String currency) {
        // --- 1. Status check ---
        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new BusinessException("Wallet is not active: " + wallet.getId());
        }

        // --- 2. Available balance check ---
        BigDecimal available = walletBalanceService.getAvailableBalance(wallet, currency);
        if (available.compareTo(amount) < 0) {
            log.warn("wallet_guard_insufficient_balance walletId={} available={} requested={} currency={}",
                    wallet.getId(), available, amount, currency);
            throw new BusinessException(
                    "Insufficient available balance. Available: " + available + " " + currency
                            + ", requested: " + amount + " " + currency);
        }

        log.debug("wallet_guard_ok walletId={} available={} requested={} currency={}",
                wallet.getId(), available, amount, currency);
    }
}
