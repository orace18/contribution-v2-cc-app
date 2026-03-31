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

    private static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";

    private final OperationRepository operationRepository;
    private final JawuPayWebhookService jawuPayWebhookService;

    @Value("${psp.jawupay.reconciliation.lookback-minutes:2}")
    private long lookbackMinutes = 2;

    @Value("${psp.jawupay.reconciliation.batch-size:200}")
    private int batchSize = 200;

    @Scheduled(cron = "${psp.jawupay.reconciliation.cron:0 */2 * * * *}",
            zone = "${psp.jawupay.reconciliation.zone:Africa/Porto-Novo}")
    public void reconcileStuckOperations() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(Math.max(1, lookbackMinutes));
        int effectiveBatchSize = Math.max(1, batchSize);

        List<Operation> stalePending = operationRepository
                .findBatchForPspReconciliation(
                        OperationStatus.PENDING_PSP,
                        RECONCILIATION_REQUIRED,
                        cutoff,
                        PageRequest.of(0, effectiveBatchSize)
                );
        if (stalePending.isEmpty()) {
            return;
        }

        for (Operation operation : stalePending) {
            try {
                String paymentId = normalizeIdentifier(operation.getPaymentId());
                if (paymentId != null) {
                    jawuPayWebhookService.checkAndApplyPaymentStatus(paymentId);
                    continue;
                }

                String payoutId = normalizeIdentifier(operation.getPayoutId());
                if (payoutId != null) {
                    jawuPayWebhookService.checkAndApplyPayoutStatus(payoutId);
                    continue;
                }

                log.warn("psp_reconciliation_skip_missing_identifiers operationId={} pspReference={}",
                        operation.getId(), operation.getPspReference());
            } catch (Exception ex) {
                log.error("psp_reconciliation_error operationId={} paymentId={} payoutId={}",
                        operation.getId(), operation.getPaymentId(), operation.getPayoutId(), ex);
            }
        }
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
