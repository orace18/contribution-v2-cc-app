package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PspReconciliationJob {

    private final OperationRepository operationRepository;
    private final JawuPayWebhookService jawuPayWebhookService;

    @Value("${psp.jawupay.reconciliation.lookback-minutes:1}")
    private long lookbackMinutes = 1;

    @Value("${psp.jawupay.reconciliation.max-age-hours:24}")
    private long maxAgeHours = 24;

    @Value("${psp.jawupay.reconciliation.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(fixedDelayString = "${psp.jawupay.reconciliation.fixed-delay-ms:60000}")
    public void reconcileStuckOperations() {
        log.info("PSP reconciliation check started");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusMinutes(Math.max(1, lookbackMinutes));
        OffsetDateTime maxAgeCutoff = now.minusHours(Math.max(1, maxAgeHours));
        int effectiveBatchSize = Math.max(1, batchSize);

        List<Operation> stalePending = operationRepository
                .findPendingPaymentBatchForPspReconciliation(
                        OperationStatus.PENDING_PSP,
                        cutoff,
                        PageRequest.of(0, effectiveBatchSize)
                );
        if (stalePending.isEmpty()) {
            return;
        }

        for (Operation operation : stalePending) {
            try {
                log.info("Checking operation {}", operation.getId());
                if (isOlderThan(operation, maxAgeCutoff)) {
                    log.warn("PSP reconciliation timeout reached for operationId={} createdAt={}",
                            operation.getId(), operation.getCreatedAt());
                    jawuPayWebhookService.markOperationAsTimedOut(operation);
                    continue;
                }
                jawuPayWebhookService.checkAndApplyPaymentStatus(operation);
            } catch (Exception ex) {
                log.error("psp_reconciliation_error operationId={} paymentId={} providerReference={}",
                        operation.getId(), operation.getPaymentId(), operation.getProviderReference(), ex);
            }
        }
    }

    private boolean isOlderThan(Operation operation, OffsetDateTime cutoff) {
        return operation.getCreatedAt() != null && !operation.getCreatedAt().isAfter(cutoff);
    }
}
