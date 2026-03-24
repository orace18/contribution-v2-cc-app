package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.dto.WalletPayRequest;
import com.ccapp.wallet.dto.WalletPayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles peer-to-peer wallet payments (POST /wallet/pay).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletPaymentService {

    private final WalletRepository walletRepository;
    private final WalletGuardService walletGuardService;
    private final WalletLimitService walletLimitService;
    private final WalletDebitFacade walletDebitFacade;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;

    public WalletPayResponse pay(UUID senderAccountHolderId, UUID actorUserId, WalletPayRequest request) {
        Wallet senderWallet = walletGuardService.requireActiveWallet(senderAccountHolderId);
        walletGuardService.checkDebitAllowed(senderWallet, request.amount(), request.currency());

        Wallet destinationWallet = walletRepository.findByMobileMoneyNumber(request.destinationPhone())
                .orElseThrow(() -> new BusinessException(
                        "No wallet found for phone: " + request.destinationPhone()));
        if (!"ACTIVE".equals(destinationWallet.getStatus())) {
            throw new BusinessException("Destination wallet is not active");
        }
        if (senderWallet.getId().equals(destinationWallet.getId())) {
            throw new BusinessException("Cannot pay to own wallet");
        }

        walletLimitService.checkDepositAllowed(destinationWallet, request.amount(), request.currency());

        AccountHolder destinationHolder = resolveHolder(destinationWallet.getAccountHolderId());
        String destinationCode = countryChartOfAccountsService.findOrCreateWallet(destinationHolder).getAccountCode();

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        senderAccountHolderId,
                        senderAccountHolderId,
                        actorUserId,
                        senderWallet,
                        OperationType.WALLET_PAYMENT,
                        request.amount(),
                        request.currency(),
                        request.idempotencyKey(),
                        request.description(),
                        buildOtpFingerprint(request),
                        destinationCode,
                        destinationWallet.getAccountHolderId(),
                        destinationWallet.getId(),
                        request.destinationPhone(),
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        false,
                        false,
                        "Wallet payment - sender debit",
                        "Wallet payment - destination credit",
                        destinationWallet.getId(),
                        destinationWallet.getAccountHolderId(),
                        WalletDebitFacade.SecureDebitPostingMode.STANDARD,
                        null
                )
        );

        if (result.otpRequired()) {
            return new WalletPayResponse(
                    result.operationId(),
                    "OTP_REQUIRED",
                    request.amount(),
                    request.currency(),
                    true,
                    result.otpChallengeId(),
                    result.otpExpiresAt()
            );
        }

        log.info("wallet_payment_done operationId={} sender={} destination={} amount={} currency={}",
                result.operationId(), senderAccountHolderId,
                destinationWallet.getAccountHolderId(), request.amount(), request.currency());

        return confirmedResponse(result.operationId(), request.amount(), request.currency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }

    private String buildOtpFingerprint(WalletPayRequest request) {
        return "wallet-payment|"
                + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency().trim().toUpperCase()
                + "|" + request.destinationPhone().trim();
    }

    private WalletPayResponse confirmedResponse(UUID operationId, java.math.BigDecimal amount, String currency) {
        return new WalletPayResponse(
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
