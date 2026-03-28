package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
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

        // Step 1 — create or retrieve idempotent operation
        Operation operation = operationService.createInitiatedOperation(
                accountHolderId, actorUserId,
                OperationType.WALLET_DEPOSIT_PSP,
                amount, currency, idempotencyKey, description
        );

        // Idempotency guard: PSP calls already done for this operation
        if (operation.getStatus() != OperationStatus.INITIATED) {
            log.info("Idempotent payment: operation={} already in status={}, skipping PSP calls",
                    operation.getId(), operation.getStatus());
            return new InitiatePaymentResult(operation.getId(), null);
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
        JawuPayConfirmResponse confirm = pspClient.confirmPayment(
                new JawuPayConfirmRequest(
                        quote.quoteId(),
                        currency,
                        JawuPayConfirmRequest.PayerInfo.forMobileMoney(msisdn, operatorHint)
                ),
                idempotencyKey
        );

        // Step 4 — store paymentId on the operation, transition to PENDING_PSP
        operationService.markPendingPsp(operation.getId(), confirm.paymentId().toString());
        log.info("Payment pending PSP: operationId={} paymentId={}", operation.getId(), confirm.paymentId());

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
}
