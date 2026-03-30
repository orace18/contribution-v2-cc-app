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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JawuPayWebhookService {

    private static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";

    private final OperationService operationService;
    private final JawuPayPspClient pspClient;
    private final AccountingPostingService accountingPostingService;
    private final WalletDepositService walletDepositService;
    private final WalletWithdrawalService walletWithdrawalService;
    private final ObjectMapper objectMapper;

    @Value("${psp.jawupay.webhook.allowedIps:${psp.jawupay.webhook.allowed-ips:}}")
    private String allowedIpsConfig;

    private Set<String> allowedIps;

    @PostConstruct
    void init() {
        allowedIps = Arrays.stream(allowedIpsConfig.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void processWebhook(JawuPayWebhookRequest request, String remoteIp) {
        validateIp(remoteIp);

        String paymentId = normalizeIdentifier(request.paymentId());
        String payoutId = normalizeIdentifier(request.payoutId());
        String providerReference = normalizeIdentifier(request.reference());
        if (paymentId == null && payoutId == null) {
            log.warn("Webhook ignored: neither paymentId nor payoutId present provider={} remoteIp={}",
                    request.provider(), remoteIp);
            return;
        }

        String auditPayload = buildAuditPayload(request, remoteIp, "RECEIVED", null);

        Optional<Operation> maybeOperation;
        try {
            maybeOperation = findOperationForWebhook(paymentId, payoutId, providerReference);
        } catch (Exception ex) {
            log.error("Webhook lookup failed provider={} paymentId={} payoutId={} providerReference={} remoteIp={} payload={}",
                    request.provider(), paymentId, payoutId, providerReference, remoteIp, auditPayload, ex);
            return;
        }

        if (maybeOperation.isEmpty()) {
            log.warn("Webhook ignored: no operation found for paymentId={} payoutId={} providerReference={} provider={} remoteIp={} payload={}",
                    paymentId, payoutId, providerReference, request.provider(), remoteIp, auditPayload);
            return;
        }

        UUID operationId = maybeOperation.get().getId();
        try {
            Operation operation = operationService.recordPspMetadata(
                    operationId,
                    new PspMetadata(
                            resolvePrimaryReference(paymentId, payoutId, providerReference),
                            paymentId,
                            payoutId,
                            providerReference,
                            null,
                            null,
                            request.status(),
                            auditPayload
                    )
            );

            applyPspStatusToOperation(operation, request.status());
        } catch (Exception ex) {
            log.error("Webhook processing failed operationId={} provider={} paymentId={} payoutId={} providerReference={} remoteIp={}",
                    operationId, request.provider(), paymentId, payoutId, providerReference, remoteIp, ex);
            markReconciliationNeeded(operationId, request, remoteIp, paymentId, payoutId, providerReference, ex);
        }
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

    private void markReconciliationNeeded(UUID operationId,
                                          JawuPayWebhookRequest request,
                                          String remoteIp,
                                          String paymentId,
                                          String payoutId,
                                          String providerReference,
                                          Exception ex) {
        String auditPayload = buildAuditPayload(
                request,
                remoteIp,
                RECONCILIATION_REQUIRED,
                ex.getClass().getSimpleName() + ": " + normalizeErrorMessage(ex.getMessage())
        );
        try {
            operationService.recordPspMetadata(
                    operationId,
                    new PspMetadata(
                            resolvePrimaryReference(paymentId, payoutId, providerReference),
                            paymentId,
                            payoutId,
                            providerReference,
                            null,
                            null,
                            RECONCILIATION_REQUIRED,
                            auditPayload
                    )
            );
        } catch (Exception reconciliationEx) {
            log.error("Failed to persist reconciliation marker for operationId={}", operationId, reconciliationEx);
        }
    }

    private Optional<Operation> findOperationForWebhook(String paymentId,
                                                        String payoutId,
                                                        String providerReference) {
        if (paymentId != null) {
            Optional<Operation> operation = operationService.findByPaymentIdOrPspReference(paymentId);
            if (operation.isPresent()) {
                return operation;
            }
        }
        if (payoutId != null) {
            Optional<Operation> operation = operationService.findByPayoutIdOrPspReference(payoutId);
            if (operation.isPresent()) {
                return operation;
            }
        }
        if (providerReference != null) {
            return operationService.findByProviderReferenceOrPspReference(providerReference);
        }
        return Optional.empty();
    }

    private String resolvePrimaryReference(String paymentId, String payoutId, String providerReference) {
        if (paymentId != null) {
            return paymentId;
        }
        if (payoutId != null) {
            return payoutId;
        }
        return providerReference;
    }

    private String buildAuditPayload(JawuPayWebhookRequest request,
                                     String remoteIp,
                                     String lifecycleState,
                                     String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receivedAt", OffsetDateTime.now().toString());
        payload.put("provider", normalizeIdentifier(request.provider()));
        payload.put("status", normalizeIdentifier(request.status()));
        payload.put("paymentId", normalizeIdentifier(request.paymentId()));
        payload.put("payoutId", normalizeIdentifier(request.payoutId()));
        payload.put("reference", normalizeIdentifier(request.reference()));
        payload.put("remoteIp", normalizeIdentifier(remoteIp));
        payload.put("lifecycleState", lifecycleState);
        if (error != null) {
            payload.put("error", error);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize webhook audit payload provider={} paymentId={} payoutId={}",
                    request.provider(), request.paymentId(), request.payoutId(), ex);
            return "{\"lifecycleState\":\"" + lifecycleState + "\"}";
        }
    }

    private String normalizeErrorMessage(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        String normalized = value.trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
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
            throw new AccessDeniedException("Webhook request from unauthorized IP: " + remoteIp);
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
