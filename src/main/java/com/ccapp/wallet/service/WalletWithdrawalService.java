package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.service.JawuPayPayoutOrchestrationService;
import com.ccapp.wallet.dto.WalletReserveRequest;
import com.ccapp.wallet.dto.WalletWithdrawRequest;
import com.ccapp.wallet.dto.WalletWithdrawResponse;
import com.ccapp.wallet.event.WalletDebitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles wallet withdrawals (wallet -> PSP payout).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletWithdrawalService {

    private static final long WITHDRAWAL_RESERVATION_EXPIRY_MINUTES = 30;

    private final WalletGuardService walletGuardService;
    private final WalletDebitFacade walletDebitFacade;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final AccountHolderRepository accountHolderRepository;
    private final WalletRepository walletRepository;
    private final OperationService operationService;
    private final JawuPayPayoutOrchestrationService jawuPayPayoutOrchestrationService;
    private final WalletReservationService walletReservationService;
    private final AccountingPostingService accountingPostingService;
    private final DomainEventPublisher eventPublisher;

    public WalletWithdrawResponse withdraw(UUID accountHolderId, UUID actorUserId,
                                           WalletWithdrawRequest request) {
        AccountHolder holder = resolveHolder(accountHolderId);
        if (holder.getKycStatus() != KycStatus.VERIFIED
                || !holder.resolveKycLevel().isAtLeast(KycLevel.LEVEL_1_BASIC)) {
            throw new BusinessException(
                    "KYC verification is required for withdrawal. Current status: "
                            + holder.getKycStatus().name()
                            + ", level: " + holder.resolveKycLevel().name());
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
                        null,
                        WalletDebitFacade.SecureDebitPostingMode.NONE,
                        countryCode
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
                    result.otpExpiresAt(),
                    null
            );
        }

        if ("PENDING_PSP".equals(result.status())) {
            Operation pending = operationService.getById(result.operationId());
            return pendingResponse(result.operationId(), request.amount(), request.currency(), pending.getPspReference());
        }
        if ("CONFIRMED".equals(result.status())) {
            Operation confirmed = operationService.getById(result.operationId());
            return new WalletWithdrawResponse(
                    confirmed.getId(),
                    confirmed.getStatus().name(),
                    request.amount(),
                    request.currency(),
                    false,
                    null,
                    null,
                    confirmed.getPspReference()
            );
        }

        Operation operation = operationService.getById(result.operationId());
        WalletReserveRequest reservationRequest = new WalletReserveRequest(
                request.amount(),
                request.currency().trim().toUpperCase(Locale.ROOT),
                reservationReason(operation.getId()),
                OffsetDateTime.now().plusMinutes(WITHDRAWAL_RESERVATION_EXPIRY_MINUTES),
                null
        );
        walletReservationService.reserveSystem(accountHolderId, reservationRequest, operation.getId());

        try {
            JawuPayPayoutOrchestrationService.InitiatePayoutResult orchestrationResult =
                    jawuPayPayoutOrchestrationService.createPayout(
                            operation.getId(),
                            countryCode,
                            resolvePayoutPaymentMethod(wallet),
                            resolvePayoutPhone(wallet, holder),
                            resolvePayoutOperatorHint(wallet)
                    );
            if (!operation.getId().equals(orchestrationResult.operationId())) {
                throw new IllegalStateException("Wallet withdrawal orchestration returned an unexpected operation id");
            }

            Operation pending = operationService.getById(orchestrationResult.operationId());

            log.info("wallet_withdrawal_pending_psp operationId={} accountHolderId={} amount={} currency={} payoutId={} pspReference={}",
                    pending.getId(),
                    accountHolderId,
                    request.amount(),
                    request.currency(),
                    pending.getPayoutId(),
                    pending.getPspReference());
            return pendingResponse(pending.getId(), request.amount(), request.currency(), pending.getPspReference());
        } catch (RuntimeException ex) {
            walletReservationService.releaseReservation(operation.getId());
            Operation latest = operationService.getById(operation.getId());
            if (latest.getStatus() != OperationStatus.CONFIRMED) {
                operationService.markFailed(operation.getId());
            }
            throw ex;
        }
    }

    public void confirmWithdrawal(UUID operationId) {
        Operation current = operationService.getById(operationId);
        boolean alreadyConfirmed = current.getStatus() == OperationStatus.CONFIRMED;
        Operation operation = alreadyConfirmed ? current : operationService.confirmOperation(operationId);
        walletReservationService.consumeReservation(operation.getId());
        Wallet wallet = walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(operation.getAccountHolderId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("Wallet not found for account holder: " + operation.getAccountHolderId()));

        accountingPostingService.postWalletWithdrawal(operation, resolveWalletAccountCode(operation.getAccountHolderId()));
        if (!alreadyConfirmed) {
            eventPublisher.publish(WalletDebitedEvent.create(
                    wallet.getId(),
                    operation.getAccountHolderId(),
                    operation.getAmount(),
                    operation.getCurrency(),
                    operation.getId()
            ));
        }
    }

    public void confirmPspWithdrawal(UUID operationId) {
        confirmWithdrawal(operationId);
    }

    public void handlePspWithdrawalFailure(UUID operationId) {
        Operation current = operationService.getById(operationId);
        if (current.getStatus() == OperationStatus.CONFIRMED) {
            log.warn("wallet_withdrawal_psp_failed_skip operationId={} reason=already_confirmed", operationId);
            return;
        }

        Operation failed = current.getStatus() == OperationStatus.FAILED
                ? current
                : operationService.markFailed(operationId);

        walletReservationService.releaseReservation(operationId);
        log.warn("wallet_withdrawal_psp_failed operationId={} accountHolderId={} amount={} currency={} reservationRelease=done",
                failed.getId(),
                failed.getAccountHolderId(),
                failed.getAmount(),
                failed.getCurrency());
    }

    private AccountHolder resolveHolder(UUID id) {
        return accountHolderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account holder not found: " + id));
    }

    private String buildOtpFingerprint(WalletWithdrawRequest request) {
        return "wallet-withdrawal|"
                + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency().trim().toUpperCase()
                + "|" + request.idempotencyKey().trim();
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

    private String resolvePayoutPhone(Wallet wallet, AccountHolder holder) {
        String phone = wallet.getMobileMoneyNumber();
        if (phone == null || phone.isBlank()) {
            phone = holder.getPhoneNumber();
        }
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("WALLET_WITHDRAWAL_PHONE_REQUIRED");
        }
        return phone.trim();
    }

    private String resolvePayoutPaymentMethod(Wallet wallet) {
        String operator = normalizeOperatorToken(resolvePayoutOperatorHint(wallet));
        if (operator.startsWith("MOBILE_MONEY_")) {
            return operator;
        }
        return "MOBILE_MONEY_" + operator;
    }

    private String resolvePayoutOperatorHint(Wallet wallet) {
        if (wallet.getProvider() == null || wallet.getProvider().isBlank()) {
            throw new BusinessException("WALLET_WITHDRAWAL_PROVIDER_REQUIRED");
        }
        return wallet.getProvider().trim();
    }

    private String normalizeOperatorToken(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String resolveWalletAccountCode(UUID accountHolderId) {
        AccountHolder holder = resolveHolder(accountHolderId);
        return countryChartOfAccountsService.findOrCreateWallet(holder).getAccountCode();
    }

    private String reservationReason(UUID operationId) {
        return "wallet-withdrawal-hold:" + operationId;
    }

    private WalletWithdrawResponse pendingResponse(UUID operationId,
                                                   BigDecimal amount,
                                                   String currency,
                                                   String pspReference) {
        return new WalletWithdrawResponse(
                operationId,
                "PENDING_PSP",
                amount,
                currency,
                false,
                null,
                null,
                pspReference
        );
    }
}
