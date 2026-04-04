package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.operation.service.OperationService.PspMetadata;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.domain.entity.JawuPayWebhookEvent;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.repository.JawuPayWebhookEventRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletWithdrawalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private static final String JAWUPAY_PROVIDER = "JAWUPAY";
    private static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String INVALID_SIGNATURE_MESSAGE = "Invalid JawuPay webhook signature";

    private final OperationService operationService;
    private final JawuPayPspClient pspClient;
    private final AccountingPostingService accountingPostingService;
    private final MlmCommissionService mlmCommissionService;
    private final MembershipService membershipService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final WalletDepositService walletDepositService;
    private final WalletWithdrawalService walletWithdrawalService;
    private final JawuPayWebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${psp.jawupay.webhook.allowedIps:${psp.jawupay.webhook.allowed-ips:}}")
    private String allowedIpsConfig;

    @Value("${psp.jawupay.webhook.hmac-secret:${psp.jawupay.hmac-secret:}}")
    private String webhookHmacSecret;

    @Value("${psp.jawupay.mock-enabled:false}")
    private boolean mockEnabled;

    private Set<String> allowedIps;
    private byte[] webhookHmacSecretBytes;

    @PostConstruct
    void init() {
        allowedIps = Arrays.stream((allowedIpsConfig == null ? "" : allowedIpsConfig).split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (!mockEnabled && allowedIps.isEmpty()) {
            throw new IllegalStateException("psp.jawupay.webhook.allowedIps must not be empty");
        }
        String normalizedSecret = normalizeIdentifier(webhookHmacSecret);
        if (!mockEnabled && normalizedSecret == null) {
            throw new IllegalStateException("psp.jawupay.webhook.hmac-secret must not be empty");
        }
        if (normalizedSecret != null) {
            webhookHmacSecretBytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public void processWebhook(JawuPayWebhookRequest request,
                               byte[] rawRequestBody,
                               String signatureHeader,
                               String remoteIp) {
        validateIp(remoteIp);
        validateSignature(rawRequestBody, signatureHeader, remoteIp);
        validateProvider(request.provider());

        String paymentId = normalizeIdentifier(request.paymentId());
        String payoutId = normalizeIdentifier(request.payoutId());
        String providerReference = normalizeIdentifier(request.resolvedProviderReference());
        validateReferencePresence(paymentId, payoutId, providerReference);

        Operation operation = findOperationForWebhook(paymentId, payoutId, providerReference)
                .orElseThrow(() -> new BusinessException("Operation not found for JawuPay webhook identifiers"));

        validateOperationIdIfPresent(request.operationId(), operation.getId());
        verifyAmountCurrencyMatch(operation, requireAmount(request.amount()), requireCurrency(request.currency()));
        recordWebhookEvent(request, operation, paymentId, payoutId, providerReference);

        String auditPayload = buildAuditPayload(request, remoteIp, "RECEIVED", null);
        try {
            Operation updatedOperation = operationService.recordPspMetadata(
                    operation.getId(),
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

            applyPspStatusToOperation(updatedOperation, request.status());
        } catch (BusinessException | ConflictException | AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Webhook processing failed operationId={} provider={} paymentId={} payoutId={} providerReference={} remoteIp={}",
                    operation.getId(), request.provider(), paymentId, payoutId, providerReference, remoteIp, ex);
            markReconciliationNeeded(operation.getId(), request, remoteIp, paymentId, payoutId, providerReference, ex);
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

        Operation updatedOperation;
        try {
            updatedOperation = operationService.recordPspMetadata(
                    operation.getId(),
                    PspMetadata.payment(
                            payment.paymentId() == null ? paymentId : payment.paymentId().toString(),
                            payment.providerReference(),
                            null,
                            payment.paymentMethodId(),
                            payment.status()
                    )
            );
        } catch (ConflictException ex) {
            log.warn("Reconciliation providerReference conflict for paymentId={}, applying status-only update", paymentId);
            updatedOperation = operationService.recordPspMetadata(
                    operation.getId(),
                    PspMetadata.statusOnly(payment.status())
            );
        }

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

        Operation updatedOperation;
        try {
            updatedOperation = operationService.recordPspMetadata(
                    operation.getId(),
                    PspMetadata.payout(
                            payout.payoutId() == null ? payoutId : payout.payoutId().toString(),
                            payout.providerReference(),
                            payout.paymentMethodId(),
                            payout.status()
                    )
            );
        } catch (ConflictException ex) {
            log.warn("Reconciliation providerReference conflict for payoutId={}, applying status-only update", payoutId);
            updatedOperation = operationService.recordPspMetadata(
                    operation.getId(),
                    PspMetadata.statusOnly(payout.status())
            );
        }

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
                if (operation.getType() == OperationType.WALLET_WITHDRAWAL) {
                    walletWithdrawalService.confirmWithdrawal(operation.getId());
                    return;
                }
                Operation confirmed = operationService.confirmOperation(operation.getId());
                dispatchConfirmedSideEffects(confirmed);
            }
            case FAILED -> {
                Operation failed = operationService.markFailed(operation.getId());
                dispatchFailedSideEffects(failed);
            }
            case PENDING -> log.info("Operation still pending per PSP: operationId={}", operation.getId());
        }
    }

    @Transactional
    void dispatchConfirmedSideEffects(Operation operation) {
        if (operation == null) {
            return;
        }
        switch (operation.getType()) {
            case WALLET_DEPOSIT_PSP -> walletDepositService.confirmPspDeposit(operation.getId());
            case WALLET_WITHDRAWAL -> walletWithdrawalService.confirmWithdrawal(operation.getId());
            case SUBSCRIPTION -> {
                membershipService.activateForConfirmedOperation(operation);
                accountingPostingService.postConfirmedOperation(operation.getId());
                mlmCommissionService.calculateForOperation(operation);
                mobileOnboardingJourneyService.onPaidPaymentConfirmed(operation);
            }
            default -> accountingPostingService.postConfirmedOperation(operation.getId());
        }
    }

    @Transactional
    void dispatchFailedSideEffects(Operation operation) {
        if (operation == null) {
            return;
        }
        if (operation.getType() == OperationType.SUBSCRIPTION) {
            membershipService.cancelPendingMembership(operation.getId());
            mobileOnboardingJourneyService.onPaidPaymentFailed(operation);
            return;
        }
        if (operation.getType() == OperationType.WALLET_WITHDRAWAL) {
            walletWithdrawalService.handlePspWithdrawalFailure(operation.getId());
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

    private void validateProvider(String provider) {
        String normalizedProvider = normalizeIdentifier(provider);
        if (normalizedProvider == null || !JAWUPAY_PROVIDER.equalsIgnoreCase(normalizedProvider)) {
            throw new BusinessException("Only JawuPay provider is accepted");
        }
    }

    private void validateReferencePresence(String paymentId,
                                           String payoutId,
                                           String providerReference) {
        if (paymentId == null && payoutId == null && providerReference == null) {
            throw new BusinessException("paymentId or payoutId or providerReference is required");
        }
    }

    private void validateOperationIdIfPresent(UUID requestOperationId, UUID operationId) {
        if (requestOperationId != null && !requestOperationId.equals(operationId)) {
            throw new BusinessException("Webhook operationId does not match stored operation");
        }
    }

    private long requireAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException("Webhook amount is required");
        }
        return amount;
    }

    private String requireCurrency(String currency) {
        String normalizedCurrency = normalizeIdentifier(currency);
        if (normalizedCurrency == null) {
            throw new BusinessException("Webhook currency is required");
        }
        return normalizedCurrency;
    }

    private void recordWebhookEvent(JawuPayWebhookRequest request,
                                    Operation operation,
                                    String paymentId,
                                    String payoutId,
                                    String providerReference) {
        String normalizedProvider = normalizeIdentifier(request.provider()).toUpperCase(Locale.ROOT);
        String normalizedStatus = normalizeIdentifier(request.status()).toUpperCase(Locale.ROOT);
        String normalizedEventId = normalizeIdentifier(request.eventId());
        String eventHash = computeEventHash(
                normalizedProvider,
                normalizedStatus,
                paymentId,
                payoutId,
                providerReference,
                operation.getId(),
                request.amount(),
                requireCurrency(request.currency())
        );

        try {
            webhookEventRepository.save(JawuPayWebhookEvent.builder()
                    .eventId(normalizedEventId)
                    .eventHash(eventHash)
                    .operationId(operation.getId())
                    .provider(normalizedProvider)
                    .status(normalizedStatus)
                    .paymentId(paymentId)
                    .payoutId(payoutId)
                    .providerReference(providerReference)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Duplicate JawuPay webhook event rejected");
        }
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
        payload.put("providerReference", normalizeIdentifier(request.resolvedProviderReference()));
        payload.put("operationId", request.operationId() == null ? null : request.operationId().toString());
        payload.put("amount", request.amount());
        payload.put("currency", normalizeIdentifier(request.currency()));
        payload.put("eventId", normalizeIdentifier(request.eventId()));
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

    private String computeEventHash(String provider,
                                    String status,
                                    String paymentId,
                                    String payoutId,
                                    String providerReference,
                                    UUID operationId,
                                    Long amount,
                                    String currency) {
        String input = String.join("|",
                safeHashValue(provider),
                safeHashValue(status),
                safeHashValue(paymentId),
                safeHashValue(payoutId),
                safeHashValue(providerReference),
                operationId == null ? "" : operationId.toString(),
                amount == null ? "" : amount.toString(),
                safeHashValue(currency));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String safeHashValue(String value) {
        return value == null ? "" : value;
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
            case "FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED", "EXPIRED", "TIMEOUT" -> InternalStatus.FAILED;
            default -> InternalStatus.PENDING;
        };
    }

    private boolean isTerminal(OperationStatus status) {
        return status == OperationStatus.CONFIRMED
                || status == OperationStatus.FAILED
                || status == OperationStatus.EXPIRED;
    }

    private void validateIp(String remoteIp) {
        if (!allowedIps.contains(remoteIp)) {
            log.warn("Webhook blocked: ip={} not in allowlist", remoteIp);
            throw new AccessDeniedException("Webhook request from unauthorized IP: " + remoteIp);
        }
    }

    private void validateSignature(byte[] rawRequestBody,
                                   String signatureHeader,
                                   String remoteIp) {
        if (rawRequestBody == null || rawRequestBody.length == 0) {
            log.warn("Webhook blocked: empty body during signature verification remoteIp={}", remoteIp);
            throw new AccessDeniedException(INVALID_SIGNATURE_MESSAGE);
        }

        byte[] providedSignature = decodeSignature(signatureHeader);
        byte[] expectedSignature = computeWebhookHmac(rawRequestBody);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            log.warn("Webhook blocked: invalid signature remoteIp={} signaturePresent={} bodyBytes={}",
                    remoteIp,
                    signatureHeader != null && !signatureHeader.isBlank(),
                    rawRequestBody.length);
            throw new AccessDeniedException(INVALID_SIGNATURE_MESSAGE);
        }
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private byte[] decodeSignature(String signatureHeader) {
        String normalizedSignature = normalizeIdentifier(signatureHeader);
        if (normalizedSignature == null) {
            throw new AccessDeniedException(INVALID_SIGNATURE_MESSAGE);
        }
        if (normalizedSignature.regionMatches(true, 0, "sha256=", 0, 7)) {
            normalizedSignature = normalizedSignature.substring(7).trim();
        }
        if ((normalizedSignature.length() & 1) != 0) {
            throw new AccessDeniedException(INVALID_SIGNATURE_MESSAGE);
        }

        byte[] decoded = new byte[normalizedSignature.length() / 2];
        for (int i = 0; i < normalizedSignature.length(); i += 2) {
            int high = Character.digit(normalizedSignature.charAt(i), 16);
            int low = Character.digit(normalizedSignature.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new AccessDeniedException(INVALID_SIGNATURE_MESSAGE);
            }
            decoded[i / 2] = (byte) ((high << 4) + low);
        }
        return decoded;
    }

    private byte[] computeWebhookHmac(byte[] rawRequestBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookHmacSecretBytes, HMAC_SHA256));
            return mac.doFinal(rawRequestBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to verify JawuPay webhook signature", ex);
        }
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
