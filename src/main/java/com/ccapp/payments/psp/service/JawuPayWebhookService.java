package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.operation.service.OperationService.PspMetadata;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletWithdrawalService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Processes JawuPay webhook notifications and provides fallback status checks.
 *
 * <p>Security: all webhook calls are validated against a configurable IP allowlist before
 * any business logic runs.
 *
 * <p>Idempotence: operations already in a terminal state (CONFIRMED / FAILED / EXPIRED)
 * are silently ignored so duplicate webhook deliveries are safe.
 *
 * <p>Shared logic: {@link #applyPspStatusToOperation(Operation, String)} is the single
 * method that drives the Operation lifecycle from a raw PSP status string. It is called
 * by both the webhook handler and the fallback checks to avoid any duplication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JawuPayWebhookService {

    private final OperationService operationService;
    private final JawuPayPspClient pspClient;
    private final AccountingPostingService accountingPostingService;
    private final WalletDepositService walletDepositService;
    private final WalletWithdrawalService walletWithdrawalService;

    @Value("${psp.jawupay.webhook.allowed-ips:}")
    private String allowedIpsConfig;

    private Set<String> allowedIps;

    @PostConstruct
    void init() {
        allowedIps = Arrays.stream(allowedIpsConfig.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public void processWebhook(JawuPayWebhookRequest request, String remoteIp) {
        validateIp(remoteIp);

        String paymentId = normalizeIdentifier(request.paymentId());
        String providerReference = normalizeIdentifier(request.reference());
        if (paymentId == null && providerReference == null) {
            log.warn("Webhook ignored: neither paymentId nor reference present in payload provider={}",
                    request.provider());
            return;
        }

        Optional<Operation> maybeOperation = findOperationForWebhook(paymentId, providerReference);
        if (maybeOperation.isEmpty()) {
            log.warn("Webhook ignored: no operation found for paymentId={} providerReference={} provider={}",
                    paymentId, providerReference, request.provider());
            return;
        }

        Operation operation = operationService.recordPspMetadata(
                maybeOperation.get().getId(),
                new PspMetadata(
                        paymentId != null ? paymentId : providerReference,
                        paymentId,
                        null,
                        providerReference,
                        null,
                        null,
                        request.status(),
                        null
                )
        );

        applyPspStatusToOperation(operation, request.status());
    }

    @Transactional
    public void checkAndApplyPaymentStatus(String paymentId) {
        Optional<Operation> maybeOperation = operationService.findByPaymentIdOrPspReference(paymentId);
        if (maybeOperation.isEmpty()) {
            log.warn("Fallback check: no operation found for paymentId={}", paymentId);
            return;
        }
        Operation operation = maybeOperation.get();

        JawuPayPaymentResponse payment = pspClient.getPayment(paymentId);
        verifyAmountCurrencyMatch(operation, payment.amount(), payment.currency());

        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                PspMetadata.payment(
                        payment.paymentId() == null ? paymentId : payment.paymentId().toString(),
                        payment.providerReference(),
                        null,
                        payment.paymentMethodId(),
                        payment.status()
                )
        );

        applyPspStatusToOperation(updatedOperation, payment.status());
    }

    @Transactional
    public void checkAndApplyPayoutStatus(String payoutId) {
        Optional<Operation> maybeOperation = operationService.findByPayoutIdOrPspReference(payoutId);
        if (maybeOperation.isEmpty()) {
            log.warn("Payout fallback check: no operation found for payoutId={}", payoutId);
            return;
        }
        Operation operation = maybeOperation.get();

        JawuPayPayoutDetailsResponse payout = pspClient.getPayout(payoutId);
        verifyAmountCurrencyMatch(operation, payout.amount(), payout.currency());

        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                PspMetadata.payout(
                        payout.payoutId() == null ? payoutId : payout.payoutId().toString(),
                        payout.providerReference(),
                        payout.paymentMethodId(),
                        payout.status()
                )
        );

        applyPspStatusToOperation(updatedOperation, payout.status());
    }

    @Transactional
    public void applyPspStatusToOperation(Operation operation, String rawPspStatus) {
        InternalStatus mapped = mapStatus(rawPspStatus);
        log.info("Applying PSP status: operationId={} rawStatus={} mappedStatus={}",
                operation.getId(), rawPspStatus, mapped);

        if (isTerminal(operation.getStatus())) {
            log.info("Idempotent: operationId={} already in status={}, ignoring rawStatus={}",
                    operation.getId(), operation.getStatus(), rawPspStatus);
            return;
        }

        switch (mapped) {
            case SUCCESS -> {
                Operation confirmed = operationService.confirmOperation(operation.getId());
                finalizeConfirmedOperation(confirmed);
            }
            case FAILED -> operationService.markFailed(operation.getId());
            case PENDING -> log.info("Operation still pending per PSP: operationId={}", operation.getId());
        }
    }

    private void finalizeConfirmedOperation(Operation operation) {
        switch (operation.getType()) {
            case WALLET_DEPOSIT_PSP -> walletDepositService.confirmPspDeposit(operation.getId());
            case WALLET_WITHDRAWAL -> walletWithdrawalService.confirmPspWithdrawal(operation.getId());
            default -> accountingPostingService.postConfirmedOperation(operation.getId());
        }
    }

    private Optional<Operation> findOperationForWebhook(String paymentId, String providerReference) {
        if (paymentId != null) {
            Optional<Operation> operation = operationService.findByPaymentIdOrPspReference(paymentId);
            if (operation.isPresent()) {
                return operation;
            }
        }
        if (providerReference != null) {
            return operationService.findByProviderReferenceOrPspReference(providerReference);
        }
        return Optional.empty();
    }

    private enum InternalStatus { SUCCESS, FAILED, PENDING }

    private InternalStatus mapStatus(String raw) {
        if (raw == null) {
            return InternalStatus.PENDING;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "CONFIRMED", "PAID", "COMPLETED" -> InternalStatus.SUCCESS;
            case "FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED" -> InternalStatus.FAILED;
            default -> InternalStatus.PENDING;
        };
    }

    private boolean isTerminal(OperationStatus status) {
        return status == OperationStatus.CONFIRMED
                || status == OperationStatus.FAILED
                || status == OperationStatus.EXPIRED;
    }

    private void validateIp(String remoteIp) {
        if (allowedIps.isEmpty()) {
            log.debug("IP allowlist empty, allowing request from ip={}", remoteIp);
            return;
        }
        if (!allowedIps.contains(remoteIp)) {
            log.warn("Webhook blocked: ip={} not in allowlist", remoteIp);
            throw new BusinessException("Webhook request from unauthorized IP: " + remoteIp);
        }
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void verifyAmountCurrencyMatch(Operation operation, long pspAmountMinorUnits, String pspCurrency) {
        BigDecimal pspAmount = BigDecimal.valueOf(pspAmountMinorUnits).movePointLeft(2);
        if (operation.getAmount().compareTo(pspAmount) != 0) {
            log.error("Amount mismatch: operationId={} expected={} pspReported={}",
                    operation.getId(), operation.getAmount(), pspAmount);
            throw new BusinessException("PSP amount mismatch for operation " + operation.getId());
        }
        if (pspCurrency != null && !operation.getCurrency().equalsIgnoreCase(pspCurrency)) {
            log.error("Currency mismatch: operationId={} expected={} pspReported={}",
                    operation.getId(), operation.getCurrency(), pspCurrency);
            throw new BusinessException("PSP currency mismatch for operation " + operation.getId());
        }
    }
}
