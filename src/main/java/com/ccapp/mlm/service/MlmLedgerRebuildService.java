package com.ccapp.mlm.service;

import com.ccapp.mlm.domain.entity.MlmLedgerRebuildJob;
import com.ccapp.mlm.domain.enums.MlmLedgerRebuildJobStatus;
import com.ccapp.mlm.repository.MlmLedgerRebuildJobRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmLedgerRebuildService {

    private final MlmLedgerRebuildJobRepository rebuildJobRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final ShopCommissionPoolRepository shopCommissionPoolRepository;
    private final MlmVolumeLedgerService mlmVolumeLedgerService;

    @Value("${mlm.ledger-rebuild.default-batch-size:200}")
    private int defaultBatchSize;

    @Transactional
    public MlmLedgerRebuildJob requestRebuild(UUID adminUserId, Integer batchSize) {
        int normalizedBatchSize = normalizeBatchSize(batchSize);
        MlmLedgerRebuildJob job = rebuildJobRepository.save(MlmLedgerRebuildJob.builder()
                .requestedByAdminId(adminUserId)
                .batchSize(normalizedBatchSize)
                .status(MlmLedgerRebuildJobStatus.PENDING)
                .build());
        log.info("mlm_ledger_rebuild_requested jobId={} requestedByAdminId={} batchSize={}",
                job.getId(), adminUserId, normalizedBatchSize);
        return job;
    }

    @Transactional
    public int processPendingJobs() {
        int processed = 0;
        while (true) {
            MlmLedgerRebuildJob pending = rebuildJobRepository
                    .findFirstByStatusOrderByRequestedAtAsc(MlmLedgerRebuildJobStatus.PENDING)
                    .orElse(null);
            if (pending == null) {
                break;
            }
            processJobInternal(pending.getId());
            processed++;
        }
        return processed;
    }

    @Transactional
    protected void processJobInternal(UUID jobId) {
        MlmLedgerRebuildJob job = rebuildJobRepository.findById(jobId).orElseThrow();
        if (job.getStatus() != MlmLedgerRebuildJobStatus.PENDING) {
            return;
        }

        job.setStatus(MlmLedgerRebuildJobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        job.setErrorMessage(null);
        rebuildJobRepository.save(job);

        long processedOrders = 0;
        try {
            int page = 0;
            while (true) {
                Page<ShopOrder> deliveredOrders = shopOrderRepository.findByStatusOrderByCreatedAtAsc(
                        ShopOrderStatus.DELIVERED,
                        PageRequest.of(page, job.getBatchSize())
                );
                if (deliveredOrders.isEmpty()) {
                    break;
                }
                for (ShopOrder order : deliveredOrders.getContent()) {
                    rebuildSingleOrder(order);
                    processedOrders++;
                }
                if (!deliveredOrders.hasNext()) {
                    break;
                }
                page++;
            }

            job.setProcessedOrders(processedOrders);
            job.setStatus(MlmLedgerRebuildJobStatus.COMPLETED);
            job.setFinishedAt(OffsetDateTime.now());
            rebuildJobRepository.save(job);
            log.info("mlm_ledger_rebuild_completed jobId={} processedOrders={}", job.getId(), processedOrders);
        } catch (RuntimeException ex) {
            job.setProcessedOrders(processedOrders);
            job.setStatus(MlmLedgerRebuildJobStatus.FAILED);
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMessage(truncate(ex.getMessage()));
            rebuildJobRepository.save(job);
            log.error("mlm_ledger_rebuild_failed jobId={} processedOrders={} error={}",
                    job.getId(), processedOrders, ex.getMessage(), ex);
        }
    }

    private void rebuildSingleOrder(ShopOrder order) {
        BigDecimal mlmVolume = shopCommissionPoolRepository.findByOrderId(order.getId())
                .map(pool -> pool.getMlmVolume())
                .orElse(BigDecimal.ZERO);
        mlmVolumeLedgerService.rebuildForDeliveredOrder(order.getBuyerId(), order.getId(), mlmVolume, order.getCreatedAt());
    }

    private int normalizeBatchSize(Integer batchSize) {
        int resolved = batchSize == null ? defaultBatchSize : batchSize;
        if (resolved < 10) {
            return 10;
        }
        return Math.min(resolved, 5000);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
