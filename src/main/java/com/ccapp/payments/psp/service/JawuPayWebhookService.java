package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.operation.service.OperationService.PspMetadata;
import com.ccapp.payments.psp.client.JawuPayPspException;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.domain.entity.JawuPayWebhookEvent;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.repository.JawuPayWebhookEventRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.payments.subscription.service.SubscriptionPaymentFinalizationService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final MembershipService membershipService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final SubscriptionPaymentFinalizationService subscriptionPaymentFinalizationService;
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

    @Value("${psp.jawupay.webhook.skip-validation:false}")
    private boolean webhookSkipValidation;

    private Set<String> allowedIps;
    private byte[] webhookHmacSecretBytes;

    @PostConstruct
    void init() {
        allowedIps = Arrays.stream((allowedIpsConfig == null ? "" : allowedIpsConfig).split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (!mockEnabled && !webhookSkipValidation && allowedIps.isEmpty()) {
            throw new IllegalStateException("psp.jawupay.webhook.allowedIps must not be empty");
        }
        String normalizedSecret = normalizeIdentifier(webhookHmacSecret);
        if (!mockEnabled && !webhookSkipValidation && normalizedSecret == null) {
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
        log.info("JawuPay webhook received provider={} rawStatus={} paymentId={} payoutId={} providerReference={} operationId={} remoteIp={}",
                request == null ? null : request.provider(),
                request == null ? null : request.status(),
                request == null ? null : request.paymentId(),
                request == null ? null : request.payoutId(),
                request == null ? null : request.resolvedProviderReference(),
                request == null || request.operationId() == null ? null : request.operationId(),
                remoteIp);
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
        if (request.amount() != null && request.amount() > 0) {
            verifyAmountCurrencyMatch(operation, request.amount(), requireCurrency(request.currency()));
        } else {
            log.warn("Webhook amount/currency not provided, skipping amount check operationId={} paymentId={}", operation.getId(), paymentId);
        }
        recordWebhookEvent(request, operation, paymentId, payoutId, providerReference);

        String auditPayload = buildAuditPayload(request, remoteIp, "RECEIVED", null);
        try {
            log.info("JawuPay webhook matched operation operationId={} currentStatus={} currentPspStatus={} paymentId={} payoutId={} providerReference={}",
                    operation.getId(),
                    operation.getStatus(),
                    operation.getPspStatus(),
                    paymentId,
                    payoutId,
                    providerReference);
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
            log.info("JawuPay webhook processing completed operationId={} rawStatus={}",
                    updatedOperation.getId(),
                    request.status());
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
        String paymentLookupReference = normalizeIdentifier(paymentId);
        if (paymentLookupReference == null) {
            log.warn("Fallback check skipped: payment lookup reference is missing");
            return;
        }

        Optional<Operation> maybeOperation = operationService.findByPaymentIdOrPspReference(paymentLookupReference);
        if (maybeOperation.isEmpty()) {
            maybeOperation = operationService.findByProviderReferenceOrPspReference(paymentLookupReference);
        }
        if (maybeOperation.isEmpty()) {
            log.warn("Fallback check: no operation found for paymentLookupReference={}", paymentLookupReference);
            return;
        }
        checkAndApplyPaymentStatus(maybeOperation.get(), paymentLookupReference);
    }

    @Transactional
    void checkAndApplyPaymentStatus(Operation operation) {
        if (operation == null) {
            return;
        }
        checkAndApplyPaymentStatus(operation, resolvePaymentLookupReference(operation));
    }

    private void checkAndApplyPaymentStatus(Operation operation, String paymentLookupReference) {
        if (paymentLookupReference == null) {
            log.warn("PSP reconciliation skipped: missing payment lookup reference operationId={}", operation.getId());
            return;
        }
        log.info("PSP reconciliation polling operationId={} operationStatus={} pspStatus={} lookupReference={}",
                operation.getId(),
                operation.getStatus(),
                operation.getPspStatus(),
                paymentLookupReference);

        JawuPayPaymentResponse payment;
        try {
            payment = pspClient.getPayment(paymentLookupReference);
        } catch (JawuPayPspException ex) {
            if (ex.isNotFoundLike()) {
                handleMissingPaymentAtPsp(operation, paymentLookupReference, ex);
                return;
            }
            throw ex;
        }
        log.info("PSP status returned: {} for operationId={} lookupReference={}",
                payment.status(), operation.getId(), paymentLookupReference);
        verifyAmountCurrencyMatch(operation, payment.amount(), payment.currency());

        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                buildReconciliationPaymentMetadata(operation, payment, paymentLookupReference)
        );

        applyPspStatusToOperation(updatedOperation, payment.status());
    }

    @Transactional
    void markOperationAsTimedOut(Operation operation) {
        if (operation == null) {
            return;
        }
        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                PspMetadata.statusOnly("TIMEOUT")
        );
        applyPspStatusToOperation(updatedOperation, "TIMEOUT");
    }

    @Transactional
    void handleMissingPaymentAtPsp(Operation operation, String paymentLookupReference, JawuPayPspException ex) {
        if (operation == null) {
            return;
        }
        log.warn("PSP reconciliation payment not found operationId={} lookupReference={} providerReference={} paymentId={} pspReference={}",
                operation.getId(),
                paymentLookupReference,
                operation.getProviderReference(),
                operation.getPaymentId(),
                operation.getPspReference());
        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                new PspMetadata(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NOT_FOUND",
                        buildPspLookupFailurePayload("PAYMENT_NOT_FOUND", paymentLookupReference, ex)
                )
        );
        applyPspStatusToOperation(updatedOperation, "NOT_FOUND");
    }

    @Transactional
    public void checkAndApplyPayoutStatus(String payoutId) {
        Optional<Operation> maybeOperation = operationService.findByPayoutIdOrPspReference(payoutId);
        if (maybeOperation.isEmpty()) {
            log.warn("Payout fallback check: no operation found for payoutId={}", payoutId);
            return;
        }
        Operation operation = maybeOperation.get();

        JawuPayPayoutDetailsResponse payout;
        try {
            payout = pspClient.getPayout(payoutId);
        } catch (JawuPayPspException ex) {
            if (ex.isNotFoundLike()) {
                handleMissingPayoutAtPsp(operation, payoutId, ex);
                return;
            }
            throw ex;
        }
        verifyAmountCurrencyMatch(operation, payout.amount(), payout.currency());

        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                buildReconciliationPayoutMetadata(operation, payout, payoutId)
        );

        applyPspStatusToOperation(updatedOperation, payout.status());
    }

    @Transactional
    void handleMissingPayoutAtPsp(Operation operation, String payoutLookupReference, JawuPayPspException ex) {
        if (operation == null) {
            return;
        }
        log.warn("PSP reconciliation payout not found operationId={} lookupReference={} providerReference={} payoutId={} pspReference={}",
                operation.getId(),
                payoutLookupReference,
                operation.getProviderReference(),
                operation.getPayoutId(),
                operation.getPspReference());
        Operation updatedOperation = operationService.recordPspMetadata(
                operation.getId(),
                new PspMetadata(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NOT_FOUND",
                        buildPspLookupFailurePayload("PAYOUT_NOT_FOUND", payoutLookupReference, ex)
                )
        );
        applyPspStatusToOperation(updatedOperation, "NOT_FOUND");
    }

    @Transactional
    public void applyPspStatusToOperation(Operation operation, String rawPspStatus) {
        InternalStatus mapped = mapStatus(rawPspStatus);
        log.info("Applying PSP status: operationId={} currentStatus={} currentPspStatus={} rawStatus={} mappedStatus={}",
                operation.getId(), operation.getStatus(), operation.getPspStatus(), rawPspStatus, mapped);

        if (isTerminal(operation.getStatus())) {
            log.info("Idempotent: operationId={} already in status={}, ignoring rawStatus={}",
                    operation.getId(), operation.getStatus(), rawPspStatus);
            return;
        }

        switch (mapped) {
            case SUCCESS -> {
                if (operation.getType() == OperationType.WALLET_WITHDRAWAL) {
                    walletWithdrawalService.confirmWithdrawal(operation.getId());
                    log.info("PSP status transition applied operationId={} targetStatus={} operationType={}",
                            operation.getId(), OperationStatus.CONFIRMED, operation.getType());
                    return;
                }
                Operation confirmed = operationService.confirmOperation(operation.getId());
                log.info("PSP status transition applied operationId={} targetStatus={} operationType={}",
                        confirmed == null ? operation.getId() : confirmed.getId(),
                        confirmed == null ? OperationStatus.CONFIRMED : confirmed.getStatus(),
                        confirmed == null ? operation.getType() : confirmed.getType());
                dispatchConfirmedSideEffects(confirmed);
            }
            case FAILED -> {
                Operation failed = operationService.markFailed(operation.getId());
                log.info("PSP status transition applied operationId={} targetStatus={} operationType={}",
                        failed == null ? operation.getId() : failed.getId(),
                        failed == null ? OperationStatus.FAILED : failed.getStatus(),
                        failed == null ? operation.getType() : failed.getType());
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
            case SUBSCRIPTION -> dispatchSubscriptionConfirmedSideEffectsAfterCommit(operation);
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

    private void dispatchSubscriptionConfirmedSideEffectsAfterCommit(Operation operation) {
        if (operation == null || operation.getId() == null) {
            return;
        }
        UUID operationId = operation.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            log.info("Scheduling subscription finalization after commit operationId={}", operationId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSubscriptionConfirmedSideEffects(operationId);
                }
            });
            return;
        }
        runSubscriptionConfirmedSideEffects(operationId);
    }

    private void runSubscriptionConfirmedSideEffects(UUID operationId) {
        try {
            subscriptionPaymentFinalizationService.finalizeConfirmedOperation(operationId);
        } catch (Exception ex) {
            log.error("subscription_confirmed_side_effects_failed operationId={} retryable=true message={}",
                    operationId,
                    normalizeErrorMessage(ex.getMessage()),
                    ex);
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

    private String resolvePaymentLookupReference(Operation operation) {
        String paymentId = normalizeIdentifier(operation.getPaymentId());
        if (paymentId != null) {
            return paymentId;
        }
        String providerReference = normalizeIdentifier(operation.getProviderReference());
        if (providerReference != null) {
            return providerReference;
        }
        return normalizeIdentifier(operation.getPspReference());
    }

    private String resolvePaymentId(JawuPayPaymentResponse payment, Operation operation) {
        if (payment.paymentId() != null) {
            return payment.paymentId().toString();
        }
        return normalizeIdentifier(operation.getPaymentId());
    }

    private PspMetadata buildReconciliationPaymentMetadata(Operation operation,
                                                           JawuPayPaymentResponse payment,
                                                           String paymentLookupReference) {
        return PspMetadata.payment(
                preferExistingWhenConflict(
                        "paymentId",
                        operation.getPaymentId(),
                        resolvePaymentId(payment, operation),
                        "lookupReference",
                        paymentLookupReference
                ),
                preferExistingWhenConflict(
                        "providerReference",
                        operation.getProviderReference(),
                        firstNonBlank(payment.providerReference(), operation.getProviderReference()),
                        "lookupReference",
                        paymentLookupReference
                ),
                null,
                preferExistingWhenConflict(
                        "paymentMethod",
                        operation.getPaymentMethod(),
                        payment.paymentMethodId(),
                        "lookupReference",
                        paymentLookupReference
                ),
                payment.status(),
                preferExistingWhenConflict(
                        "pspCountry",
                        operation.getPspCountry(),
                        payment.country(),
                        "lookupReference",
                        paymentLookupReference
                )
        );
    }

    private PspMetadata buildReconciliationPayoutMetadata(Operation operation,
                                                          JawuPayPayoutDetailsResponse payout,
                                                          String payoutLookupReference) {
        String payoutId = payout.payoutId() == null ? payoutLookupReference : payout.payoutId().toString();
        return PspMetadata.payout(
                preferExistingWhenConflict(
                        "payoutId",
                        operation.getPayoutId(),
                        payoutId,
                        "payoutId",
                        payoutLookupReference
                ),
                preferExistingWhenConflict(
                        "providerReference",
                        operation.getProviderReference(),
                        payout.providerReference(),
                        "payoutId",
                        payoutLookupReference
                ),
                preferExistingWhenConflict(
                        "paymentMethod",
                        operation.getPaymentMethod(),
                        payout.paymentMethodId(),
                        "payoutId",
                        payoutLookupReference
                ),
                payout.status()
        );
    }

    private String preferExistingWhenConflict(String fieldLabel,
                                              String existingValue,
                                              String candidateValue,
                                              String lookupLabel,
                                              String lookupValue) {
        String normalizedExisting = normalizeIdentifier(existingValue);
        String normalizedCandidate = normalizeIdentifier(candidateValue);
        if (normalizedCandidate == null) {
            return normalizedExisting;
        }
        if (normalizedExisting != null && !normalizedExisting.equals(normalizedCandidate)) {
            log.warn("Reconciliation {} conflict for {}={}, keeping existing value", fieldLabel, lookupLabel, lookupValue);
            return normalizedExisting;
        }
        return normalizedCandidate;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeIdentifier(first);
        if (normalizedFirst != null) {
            return normalizedFirst;
        }
        return normalizeIdentifier(second);
    }

    private String buildPspLookupFailurePayload(String failureType,
                                                String lookupReference,
                                                JawuPayPspException ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("failureType", failureType);
        payload.put("lookupReference", lookupReference);
        payload.put("httpStatusCode", ex.httpStatusCode());
        payload.put("message", normalizeErrorMessage(ex.getMessage()));
        payload.put("responseBody", normalizeErrorMessage(ex.responseBody()));
        payload.put("recordedAt", OffsetDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException jsonProcessingException) {
            log.warn("Failed to serialize PSP lookup failure payload lookupReference={}", lookupReference, jsonProcessingException);
            return "{\"failureType\":\"" + failureType + "\",\"lookupReference\":\"" + lookupReference + "\"}";
        }
    }

    private enum InternalStatus { SUCCESS, FAILED, PENDING }

    private InternalStatus mapStatus(String raw) {
        if (raw == null) {
            return InternalStatus.PENDING;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "CONFIRMED", "PAID", "COMPLETED" -> InternalStatus.SUCCESS;
            case "FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED", "EXPIRED", "TIMEOUT", "NOT_FOUND" -> InternalStatus.FAILED;
            default -> InternalStatus.PENDING;
        };
    }

    private boolean isTerminal(OperationStatus status) {
        return status == OperationStatus.CONFIRMED
                || status == OperationStatus.FAILED
                || status == OperationStatus.EXPIRED;
    }

    private void validateIp(String remoteIp) {
        if (mockEnabled || webhookSkipValidation) {
            return;
        }
        if (!allowedIps.contains(remoteIp)) {
            log.warn("Webhook blocked: ip={} not in allowlist", remoteIp);
            throw new AccessDeniedException("Webhook request from unauthorized IP: " + remoteIp);
        }
    }

    private void validateSignature(byte[] rawRequestBody,
                                   String signatureHeader,
                                   String remoteIp) {
        if (mockEnabled || webhookSkipValidation) {
            log.warn("Webhook signature validation skipped: skip-validation={} mock={} remoteIp={}", webhookSkipValidation, mockEnabled, remoteIp);
            return;
        }
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
