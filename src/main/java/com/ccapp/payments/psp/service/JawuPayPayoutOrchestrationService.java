package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.operation.service.OperationService.OperationRouting;
import com.ccapp.payments.operation.service.OperationService.PspMetadata;
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
 * Orchestrates the JawuPay payout flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JawuPayPayoutOrchestrationService {

    private static final String PENDING_PSP_STATUS = "PENDING_PSP";

    private final OperationService operationService;
    private final JawuPayPspClient pspClient;

    public record InitiatePayoutResult(UUID operationId, UUID payoutId) {}

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
        return createPayout(
                accountHolderId,
                actorUserId,
                amount,
                currency,
                country,
                paymentMethodId,
                beneficiaryRef,
                operatorHint,
                idempotencyKey,
                description
        );
    }

    @Transactional
    public InitiatePayoutResult createPayout(UUID accountHolderId,
                                             UUID actorUserId,
                                             BigDecimal amount,
                                             String currency,
                                             String country,
                                             String paymentMethodId,
                                             String beneficiaryRef,
                                             String operatorHint,
                                             String idempotencyKey,
                                             String description) {
        Operation operation = operationService.createPayoutInitiatedOperation(
                accountHolderId,
                actorUserId,
                OperationType.WALLET_WITHDRAWAL,
                amount,
                currency,
                idempotencyKey,
                description,
                OperationRouting.none()
        );
        return createPayout(operation, country, paymentMethodId, beneficiaryRef, operatorHint);
    }

    @Transactional
    public InitiatePayoutResult createPayout(UUID operationId,
                                             String country,
                                             String paymentMethodId,
                                             String beneficiaryRef,
                                             String operatorHint) {
        return createPayout(operationService.getById(operationId), country, paymentMethodId, beneficiaryRef, operatorHint);
    }

    private InitiatePayoutResult createPayout(Operation operation,
                                              String country,
                                              String paymentMethodId,
                                              String beneficiaryRef,
                                              String operatorHint) {
        if (operation.getStatus() == OperationStatus.PENDING_PSP
                || operation.getStatus() == OperationStatus.CONFIRMED) {
            log.info("Idempotent payout: operation={} already in status={}, skipping PSP call",
                    operation.getId(), operation.getStatus());
            return new InitiatePayoutResult(operation.getId(), resolveExistingPayoutId(operation));
        }
        if (operation.getStatus() != OperationStatus.PAYOUT_INITIATED
                && operation.getStatus() != OperationStatus.INITIATED) {
            throw new BusinessException("Payout cannot be created for operation status: " + operation.getStatus());
        }

        long amountMinorUnits = JawuPayPaymentOrchestrationService.toMinorUnits(operation.getAmount());
        JawuPayCreatePayoutResponse payout = pspClient.createPayout(
                new JawuPayCreatePayoutRequest(
                        amountMinorUnits,
                        operation.getCurrency(),
                        country,
                        paymentMethodId,
                        operation.getAccountHolderId().toString(),
                        beneficiaryRef,
                        operatorHint
                )
        );
        log.info("Payout submitted: operationId={} payoutId={} status={}",
                operation.getId(), payout.payoutId(), payout.status());

        operationService.markPendingPsp(operation.getId(), PspMetadata.payout(
                payout.payoutId() == null ? null : payout.payoutId().toString(),
                payout.providerReference(),
                paymentMethodId,
                PENDING_PSP_STATUS
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
