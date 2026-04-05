package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.wallet.domain.entity.WalletAmlAlert;
import com.ccapp.wallet.domain.entity.WalletLimitConfig;
import com.ccapp.wallet.repository.WalletAmlAlertRepository;
import com.ccapp.wallet.repository.WalletLimitConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * AML wallet limit enforcement.
 *
 * <p>Maximum balance limits are configured per (country, KYC level, currency).
 * If a deposit would push the balance over the limit, the transaction is blocked
 * and an alert is logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletLimitService {

    private final WalletLimitConfigRepository limitConfigRepository;
    private final WalletAmlAlertRepository alertRepository;
    private final WalletBalanceService walletBalanceService;
    private final AccountHolderRepository accountHolderRepository;

    /**
     * Verifies that the wallet can receive {@code depositAmount} without breaching
     * the AML max-balance ceiling for the account holder's country and KYC level.
     *
     * @throws BusinessException (with logged alert) if the limit would be exceeded
     */
    @Transactional
    public void checkDepositAllowed(Wallet wallet, BigDecimal depositAmount, String currency) {
        AccountHolder holder = accountHolderRepository.findById(wallet.getAccountHolderId())
                .orElseThrow(() -> new BusinessException("Account holder not found"));

        String countryCode = resolveCountryCode(holder);
        String kycLevel = holder.resolveKycLevel().name();

        Optional<WalletLimitConfig> limitOpt = limitConfigRepository
                .findByCountryCodeAndKycLevelAndCurrency(countryCode, kycLevel, currency);

        if (limitOpt.isEmpty()) {
            // No limit configured for this combination – allow by default
            return;
        }

        BigDecimal maxBalance = limitOpt.get().getMaxBalance();
        BigDecimal currentBalance = walletBalanceService.getMainBalance(wallet, currency);
        BigDecimal projectedBalance = currentBalance.add(depositAmount);

        if (projectedBalance.compareTo(maxBalance) > 0) {
            alertRepository.save(WalletAmlAlert.builder()
                    .walletId(wallet.getId())
                    .accountHolderId(wallet.getAccountHolderId())
                    .alertType("MAX_BALANCE_EXCEEDED")
                    .amount(depositAmount)
                    .currency(currency)
                    .resolved(false)
                    .build());

            log.warn("wallet_aml_limit_blocked walletId={} accountHolderId={} country={} kycLevel={} " +
                            "current={} deposit={} projected={} max={} currency={}",
                    wallet.getId(), wallet.getAccountHolderId(), countryCode, kycLevel,
                    currentBalance, depositAmount, projectedBalance, maxBalance, currency);

            throw new BusinessException(
                    "Deposit blocked: would exceed maximum allowed balance of "
                            + maxBalance + " " + currency
                            + ". Current: " + currentBalance + ", requested: " + depositAmount);
        }
    }

    private String resolveCountryCode(AccountHolder holder) {
        if (holder.getServiceCountryCode() != null && !holder.getServiceCountryCode().isBlank()) {
            return holder.getServiceCountryCode().toUpperCase();
        }
        if (holder.getCountryCode() != null && !holder.getCountryCode().isBlank()) {
            return holder.getCountryCode().toUpperCase();
        }
        return "INT";
    }
}
