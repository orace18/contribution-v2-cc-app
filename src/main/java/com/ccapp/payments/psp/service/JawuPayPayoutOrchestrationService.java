package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.operation.service.OperationService.PspMetadata;
import com.ccapp.payments.operation.service.OperationService.OperationRouting;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Orchestrates the JawuPay payout flow: create Operation (PAYOUT_INITIATED) → createPayout.
 * <p>
 * The payout lifecycle then proceeds asynchronously via the webhook handler
 * ({@link JawuPayWebhookService#processWebhook}) or the fallback check
 * ({@link JawuPayWebhookService#checkAndApplyPayoutStatus}).
 * <p>
 * Amount convention: same as payment — internal {@link BigDecimal} major units converted
 * to {@code long} minor units (×100) at the PSP boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JawuPayPayoutOrchestrationService {

    private final OperationService operationService;
    private final JawuPayPspClient pspClient;

    public record InitiatePayoutResult(UUID operationId, UUID payoutId) {}

    /**
     * Initiates an outbound payout:
     * <ol>
     *   <li>Create Operation in PAYOUT_INITIATED state (idempotent).</li>
     *   <li>Call {@code POST /api/v1/payout} via the PSP adapter.</li>
     *   <li>Store the returned {@code payoutId} as the Operation's PSP reference.</li>
     *   <li>Return both the operationId and payoutId to the caller.</li>
     * </ol>
     *
     * @param beneficiaryRef  phone number or account reference of the payout recipient
     * @param operatorHint    optional mobile operator hint (e.g. "MTN", "ORANGE")
     */
    @Transactional
    public InitiatePayoutResult initiatePayout(UUID accountHolderId,
                                               UUID actorUserId,
                                               BigDecimal amount,
                                               String currency,
                                               String country,
                                               String paymentMethodId,
                                               String beneficiaryRef,
                                               String operatorHint,
                                               String idempotencyKey,
                                               String description) {

        // Step 1 — create or retrieve idempotent operation in PAYOUT_INITIATED state
        Operation operation = operationService.createPayoutInitiatedOperation(
                accountHolderId, actorUserId,
                OperationType.WALLET_WITHDRAWAL,
                amount, currency, idempotencyKey, description,
                OperationRouting.none()
        );

        // Idempotency guard: PSP call already done for this operation
        if (operation.getStatus() != OperationStatus.PAYOUT_INITIATED) {
            log.info("Idempotent payout: operation={} already in status={}, skipping PSP call",
                    operation.getId(), operation.getStatus());
            UUID existingPayoutId = resolveExistingPayoutId(operation);
            return new InitiatePayoutResult(operation.getId(), existingPayoutId);
        }

        long amountMinorUnits = JawuPayPaymentOrchestrationService.toMinorUnits(amount);

        // Step 2 — submit the payout to the PSP
        JawuPayCreatePayoutResponse payout = pspClient.createPayout(
                new JawuPayCreatePayoutRequest(
                        amountMinorUnits,
                        currency,
                        country,
                        paymentMethodId,
                        accountHolderId.toString(),
                        beneficiaryRef,
                        operatorHint
                )
        );
        log.info("Payout submitted: operationId={} payoutId={} status={}",
                operation.getId(), payout.payoutId(), payout.status());

        // Step 3 — store payoutId on the operation, transition to PENDING_PSP
        operationService.markPendingPsp(operation.getId(), PspMetadata.payout(
                payout.payoutId() == null ? null : payout.payoutId().toString(),
                payout.providerReference(),
                paymentMethodId,
                payout.status()
        ));
        log.info("Payout pending PSP: operationId={} payoutId={}", operation.getId(), payout.payoutId());

        return new InitiatePayoutResult(operation.getId(), payout.payoutId());
    }

    private UUID resolveExistingPayoutId(Operation operation) {
        if (operation.getPayoutId() != null) {
            return UUID.fromString(operation.getPayoutId());
        }
        if (operation.getPspReference() == null) {
            return null;
        }
        try {
            return UUID.fromString(operation.getPspReference());
        } catch (IllegalArgumentException ex) {
            log.warn("Existing payout operation {} has non-UUID legacy pspReference={}",
                    operation.getId(), operation.getPspReference());
            return null;
        }
    }
}
