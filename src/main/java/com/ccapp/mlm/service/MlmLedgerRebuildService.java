package com.ccapp.mlm.service;

import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmLedgerRebuildService {

    private final ShopOrderRepository shopOrderRepository;
    private final ShopCommissionPoolRepository shopCommissionPoolRepository;
    private final MlmVolumeLedgerService mlmVolumeLedgerService;

    public MlmLedgerRebuildResult rebuildDeliveredOrders(int batchSize) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        int pageIndex = 0;
        int processedOrders = 0;
        int ordersWithPool = 0;
        int rebuiltEntries = 0;

        Page<ShopOrder> page;
        do {
            page = shopOrderRepository.findByStatusOrderByCreatedAtAsc(
                    ShopOrderStatus.DELIVERED,
                    PageRequest.of(pageIndex, batchSize)
            );
            for (ShopOrder order : page.getContent()) {
                processedOrders++;
                var poolOpt = shopCommissionPoolRepository.findByOrderId(order.getId());
                if (poolOpt.isEmpty()) {
                    continue;
                }
                ordersWithPool++;
                rebuiltEntries += mlmVolumeLedgerService.rebuildForDeliveredOrder(
                        order.getBuyerId(),
                        order.getId(),
                        poolOpt.get().getMlmVolume(),
                        order.getCreatedAt()
                );
            }
            pageIndex++;
        } while (page.hasNext());

        OffsetDateTime finishedAt = OffsetDateTime.now();
        log.info("mlm_ledger_rebuild_completed processedOrders={} ordersWithPool={} ledgerEntriesRebuilt={} batchSize={} durationMs={}",
                processedOrders,
                ordersWithPool,
                rebuiltEntries,
                batchSize,
                java.time.Duration.between(startedAt, finishedAt).toMillis());
        return new MlmLedgerRebuildResult(
                batchSize,
                processedOrders,
                ordersWithPool,
                rebuiltEntries,
                startedAt,
                finishedAt
        );
    }
}
