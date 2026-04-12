package com.ccapp.payments.psp.service;

import com.ccapp.mobile.sponsor.service.PhoneValidator;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.operation.service.OperationService.PspMetadata;
import com.ccapp.payments.psp.client.JawuPayPspException;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayConfirmResponse;
import com.ccapp.payments.psp.dto.JawuPayQuoteRequest;
import com.ccapp.payments.psp.dto.JawuPayQuoteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Orchestrates the JawuPay two-step payment flow: quote → confirm.
 * <p>
 * Data flow: this service → {@link JawuPayPspClient} (adapter) → JawuPay PSP.
 * The {@link Operation} is the source of truth; the PSP response only informs it.
 * <p>
 * Amount convention: the internal Operation stores amounts as {@link BigDecimal} in
 * major units (e.g. 1500.00 XOF). JawuPay requires minor units as {@code long}
 * (e.g. 150000 for 1 500 XOF). Conversion is done here at the PSP boundary using
 * {@link #toMinorUnits(BigDecimal)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JawuPayPaymentOrchestrationService {

    private static final String PENDING_PSP_STATUS = "PENDING_PSP";

    private final OperationService operationService;
    private final JawuPayPspClient pspClient;

    public record InitiatePaymentResult(UUID operationId, String redirectUrl) {}

    /**
     * Initiates a payment:
     * <ol>
     *   <li>Create Operation in INITIATED state (idempotent).</li>
     *   <li>Create a quote via the PSP adapter.</li>
     *   <li>Confirm the payment via the PSP adapter (with idempotency key).</li>
     *   <li>Store the returned {@code paymentId} as the Operation's PSP reference.</li>
     *   <li>Return the {@code redirectUrl} to the caller.</li>
     * </ol>
     *
     * @param msisdn        payer's mobile number (required for mobile-money methods)
     * @param operatorHint  optional mobile operator hint (e.g. "MTN", "ORANGE")
     */
    @Transactional
    public InitiatePaymentResult initiatePayment(UUID accountHolderId,
                                                 UUID actorUserId,
                                                 BigDecimal amount,
                                                 String currency,
                                                 String country,
                                                 String paymentMethodId,
                                                 String msisdn,
                                                 String operatorHint,
                                                 String idempotencyKey,
                                                 String description) {
        return initiatePayment(
                accountHolderId,
                actorUserId,
                amount,
                currency,
                country,
                paymentMethodId,
                msisdn,
                operatorHint,
                idempotencyKey,
                description,
                OperationType.WALLET_DEPOSIT_PSP
        );
    }

    @Transactional
    public InitiatePaymentResult initiatePayment(UUID accountHolderId,
                                                 UUID actorUserId,
                                                 BigDecimal amount,
                                                 String currency,
                                                 String country,
                                                 String paymentMethodId,
                                                 String msisdn,
                                                 String operatorHint,
                                                 String idempotencyKey,
                                                 String description,
                                                 OperationType operationType) {
        return initiatePayment(
                accountHolderId,
                actorUserId,
                amount,
                currency,
                country,
                paymentMethodId,
                msisdn,
                operatorHint,
                idempotencyKey,
                description,
                operationType,
                null
        );
    }

    @Transactional(noRollbackFor = JawuPayPspException.class)
    public InitiatePaymentResult initiatePayment(UUID accountHolderId,
                                                 UUID actorUserId,
                                                 BigDecimal amount,
                                                 String currency,
                                                 String country,
                                                 String paymentMethodId,
                                                 String msisdn,
                                                 String operatorHint,
                                                 String idempotencyKey,
                                                 String description,
                                                 OperationType operationType,
                                                 UUID planId) {
        if (operationType == null) {
            throw new IllegalArgumentException("Operation type is required");
        }
        // Step 1 — create or retrieve idempotent operation
        Operation operation = planId == null
                ? operationService.createInitiatedOperation(
                        accountHolderId, actorUserId,
                        operationType,
                        amount, currency, idempotencyKey, description
                )
                : operationService.createInitiatedOperation(
                        accountHolderId, actorUserId,
                        operationType,
                        amount, currency, idempotencyKey, description, planId
                );

        // Idempotency guard: PSP calls already done for this operation
        if (operation.getStatus() != OperationStatus.INITIATED) {
            log.info("Idempotent payment: operation={} already in status={}, skipping PSP calls",
                    operation.getId(), operation.getStatus());
            return new InitiatePaymentResult(operation.getId(), operation.getRedirectUrl());
        }

        long amountMinorUnits = toMinorUnits(amount);

        // Step 2 — get a quote from the PSP
        JawuPayQuoteResponse quote = pspClient.createQuote(
                new JawuPayQuoteRequest(
                        amountMinorUnits,
                        country,
                        paymentMethodId,
                        accountHolderId.toString(),
                        operatorHint
                )
        );
        log.info("Quote obtained: operationId={} quoteId={} fees={}",
                operation.getId(), quote.quoteId(), quote.fees());

        // Step 3 — confirm the payment using the quote, send idempotency key as header
        String normalizedMsisdn = PhoneValidator.normalizeToE164(msisdn, country);
        if (normalizedMsisdn != null) {
            log.warn("PSP confirm msisdn normalization provider=jawupay operationId={} planId={} country={} rawMsisdn={} normalizedMsisdn={}",
                    operation.getId(),
                    safeUuid(planId),
                    country,
                    msisdn,
                    normalizedMsisdn);
        }
        if (operationType == OperationType.SUBSCRIPTION) {
            log.warn("PSP confirm preflight provider=jawupay operationId={} planId={} paymentMethodId={} operatorHint={} msisdn={} quoteId={} currency={} idempotencyKey={}",
                    operation.getId(),
                    safeUuid(planId),
                    paymentMethodId,
                    operatorHint,
                    normalizedMsisdn,
                    safeUuid(quote.quoteId()),
                    currency,
                    idempotencyKey);
        }
        JawuPayConfirmResponse confirm = pspClient.confirmPayment(
                new JawuPayConfirmRequest(
                        quote.quoteId(),
                        currency,
                        JawuPayConfirmRequest.PayerInfo.forMobileMoney(normalizedMsisdn, operatorHint)
                ),
                idempotencyKey
        );

        // Step 4 — store paymentId on the operation, transition to PENDING_PSP
        String resolvedPspPaymentId = confirm.paymentId() != null 
                ? confirm.paymentId().toString() 
                : quote.quoteId().toString();

        operationService.markPendingPsp(operation.getId(), PspMetadata.payment(
                resolvedPspPaymentId,
                confirm.providerReference(),
                confirm.redirectUrl(),
                paymentMethodId,
                PENDING_PSP_STATUS,
                country
        ));
        log.info("Payment pending PSP: operationId={} paymentId={}", operation.getId(), resolvedPspPaymentId);

        // Step 5 — return redirectUrl to the caller
        return new InitiatePaymentResult(operation.getId(), confirm.redirectUrl());
    }

    /**
     * Converts a major-unit {@link BigDecimal} amount to JawuPay minor units (×100).
     * e.g. 1500.00 XOF → 150000
     */
    static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private String safeUuid(UUID value) {
        return value == null ? "n/a" : value.toString();
    }
}
