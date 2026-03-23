package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.dto.WalletWithdrawRequest;
import com.ccapp.wallet.dto.WalletWithdrawResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles wallet withdrawals (wallet -> PSP payout).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletWithdrawalService {

    private final WalletGuardService walletGuardService;
    private final WalletDebitFacade walletDebitFacade;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;

    public WalletWithdrawResponse withdraw(UUID accountHolderId, UUID actorUserId,
                                           WalletWithdrawRequest request) {
        AccountHolder holder = resolveHolder(accountHolderId);
        if (holder.getKycStatus() != KycStatus.VERIFIED) {
            throw new BusinessException(
                    "KYC verification is required for withdrawal. Current status: "
                            + holder.getKycStatus().name());
        }

        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        walletGuardService.checkDebitAllowed(wallet, request.amount(), request.currency());

        String countryCode = readCountryCode(holder);
        String pspCode = countryChartOfAccountsService
                .resolveSystemAccount(countryCode, CountryAccountSystemTag.PSP_CLEARING)
                .getAccountCode();

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        accountHolderId,
                        accountHolderId,
                        actorUserId,
                        wallet,
                        OperationType.WALLET_WITHDRAWAL,
                        request.amount(),
                        request.currency(),
                        request.idempotencyKey(),
                        "Wallet withdrawal - payout via JawuPay",
                        buildOtpFingerprint(request),
                        pspCode,
                        null,
                        null,
                        null,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        false,
                        false,
                        "Wallet withdrawal debit",
                        "PSP payout clearing credit",
                        null,
                        null
                )
        );

        if (result.otpRequired()) {
            return new WalletWithdrawResponse(
                    result.operationId(),
                    "OTP_REQUIRED",
                    request.amount(),
                    request.currency(),
                    true,
                    result.otpChallengeId(),
                    result.otpExpiresAt()
            );
        }

        log.info("wallet_withdrawal_done operationId={} accountHolderId={} amount={} currency={}",
                result.operationId(), accountHolderId, request.amount(), request.currency());

        return confirmedResponse(result.operationId(), request.amount(), request.currency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }

    private String buildOtpFingerprint(WalletWithdrawRequest request) {
        return "wallet-withdrawal|"
                + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency().trim().toUpperCase();
    }

    private String readCountryCode(AccountHolder holder) {
        if (holder.getServiceCountryCode() != null && !holder.getServiceCountryCode().isBlank()) {
            return holder.getServiceCountryCode().trim().toUpperCase();
        }
        if (holder.getCountryCode() != null && !holder.getCountryCode().isBlank()) {
            return holder.getCountryCode().trim().toUpperCase();
        }
        return "INT";
    }

    private WalletWithdrawResponse confirmedResponse(UUID operationId, java.math.BigDecimal amount, String currency) {
        return new WalletWithdrawResponse(
                operationId,
                "CONFIRMED",
                amount,
                currency,
                false,
                null,
                null
        );
    }
}
