package com.ccapp.mlm.scheduler;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.mlm.service.MlmLedgerRebuildResult;
import com.ccapp.mlm.service.MlmLedgerRebuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlmLedgerRebuildJob {

    private final MlmLedgerRebuildService mlmLedgerRebuildService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${mlm.ledger-rebuild.scheduled-enabled:false}")
    private boolean scheduledEnabled;

    @Value("${mlm.ledger-rebuild.default-batch-size:200}")
    private int defaultBatchSize;

    @Scheduled(cron = "${mlm.ledger-rebuild.cron:0 0 4 1 1 ?}", zone = "${mlm.group-bonus.zone:Africa/Porto-Novo}")
    public void scheduledRun() {
        if (!scheduledEnabled) {
            return;
        }
        triggerManualRebuild(defaultBatchSize);
    }

    public MlmLedgerRebuildResult triggerManualRebuild(Integer requestedBatchSize) {
        int batchSize = normalizeBatchSize(requestedBatchSize);
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("MLM ledger rebuild is already running");
        }
        try {
            return mlmLedgerRebuildService.rebuildDeliveredOrders(batchSize);
        } finally {
            running.set(false);
        }
    }

    private int normalizeBatchSize(Integer requestedBatchSize) {
        if (requestedBatchSize == null || requestedBatchSize <= 0) {
            return defaultBatchSize;
        }
        return Math.min(requestedBatchSize, 1000);
    }
}
