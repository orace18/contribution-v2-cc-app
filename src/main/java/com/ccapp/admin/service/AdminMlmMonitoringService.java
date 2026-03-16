package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminCommissionPayoutBatchResponse;
import com.ccapp.admin.dto.AdminCommissionPayoutItemResponse;
import com.ccapp.admin.dto.AdminCommissionResponse;
import com.ccapp.admin.dto.AdminMlmNetworkHealthResponse;
import com.ccapp.admin.dto.AdminWeeklyVolumeResponse;
import com.ccapp.mlm.domain.enums.WeeklyBonusJobRunStatus;
import com.ccapp.mlm.service.MlmAnalyticsService;
import com.ccapp.mlm.repository.MlmMemberMetricsRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.MlmWeeklyBonusJobRunRepository;
import com.ccapp.mlm.repository.MlmWeeklySnapshotRepository;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionPayoutBatchRepository;
import com.ccapp.payments.commission.repository.CommissionPayoutItemRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMlmMonitoringService {

    private final MlmMemberMetricsRepository mlmMemberMetricsRepository;
    private final MlmWeeklySnapshotRepository mlmWeeklySnapshotRepository;
    private final MlmWeeklyBonusJobRunRepository mlmWeeklyBonusJobRunRepository;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    private final MlmAnalyticsService mlmAnalyticsService;
    private final ShopOrderRepository shopOrderRepository;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final CommissionPayoutBatchRepository commissionPayoutBatchRepository;
    private final CommissionPayoutItemRepository commissionPayoutItemRepository;

    @Value("${mlm.monitoring.weekly-bonus.max-stale-days:8}")
    private int weeklyBonusMaxStaleDays;

    @Value("${mlm.monitoring.commission-spike.lookback-hours:24}")
    private int spikeLookbackHours;

    @Value("${mlm.monitoring.commission-spike.threshold:10000}")
    private BigDecimal spikeThreshold;

    @Transactional(readOnly = true)
    public AdminMlmNetworkHealthResponse getNetworkHealth() {
        int currentWeekId = mlmAnalyticsService.currentWeekId();
        long membersWithMetrics = mlmMemberMetricsRepository.count();
        long weeklySnapshotsCount = mlmWeeklySnapshotRepository.countByWeekId(currentWeekId);
        long deliveredOrdersWithoutVolumeLedger = shopOrderRepository.countDeliveredOrdersWithoutVolumeLedger();
        boolean volumeLedgerUsageHealthy = deliveredOrdersWithoutVolumeLedger == 0;

        OffsetDateTime now = OffsetDateTime.now();
        boolean weeklyBonusExecutionHealthy = mlmWeeklyBonusJobRunRepository.findTopByOrderByFinishedAtDesc()
                .filter(run -> run.getStatus() == WeeklyBonusJobRunStatus.SUCCESS)
                .map(run -> run.getFinishedAt().isAfter(now.minusDays(weeklyBonusMaxStaleDays)))
                .orElse(false);

        long abnormalCommissionSpikes = mlmCommissionRepository.countPotentialSpikesSince(
                now.minusHours(spikeLookbackHours),
                spikeThreshold
        );

        return new AdminMlmNetworkHealthResponse(
                now,
                currentWeekId,
                membersWithMetrics,
                weeklySnapshotsCount,
                weeklyBonusExecutionHealthy,
                deliveredOrdersWithoutVolumeLedger,
                volumeLedgerUsageHealthy,
                abnormalCommissionSpikes
        );
    }

    @Transactional(readOnly = true)
    public List<AdminCommissionResponse> listCommissions(Integer weekId,
                                                         CommissionType commissionType,
                                                         CommissionStatus commissionStatus,
                                                         int page,
                                                         int size) {
        return mlmCommissionRepository.searchForAdmin(
                        weekId,
                        commissionType,
                        commissionStatus,
                        PageRequest.of(Math.max(page, 0), normalizePageSize(size))
                ).stream()
                .map(this::toCommissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCommissionPayoutBatchResponse> listCommissionPayouts(int page, int size) {
        return commissionPayoutBatchRepository.findAllByOrderByCreatedAtDesc(
                        PageRequest.of(Math.max(page, 0), normalizePageSize(size))
                ).stream()
                .map(batch -> new AdminCommissionPayoutBatchResponse(
                        batch.getId(),
                        batch.getWeekId(),
                        batch.getStatus(),
                        batch.getCreatedAt(),
                        commissionPayoutItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()).stream()
                                .map(item -> new AdminCommissionPayoutItemResponse(
                                        item.getId(),
                                        item.getAccountHolderId(),
                                        item.getGrossAmount(),
                                        item.getTaxAmount(),
                                        item.getNetAmount(),
                                        item.getStatus(),
                                        item.getOperationId(),
                                        item.getProcessedAt()
                                ))
                                .toList()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminWeeklyVolumeResponse> listWeeklyVolume(int limit) {
        return mlmVolumeLedgerRepository.summarizeWeeklyVolume(PageRequest.of(0, Math.max(limit, 1))).stream()
                .map(summary -> new AdminWeeklyVolumeResponse(
                        summary.getWeekId(),
                        summary.getTotalVolume(),
                        summary.getEntriesCount()
                ))
                .toList();
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 200);
    }

    private AdminCommissionResponse toCommissionResponse(MlmCommission commission) {
        return new AdminCommissionResponse(
                commission.getId(),
                commission.getBeneficiaryAccountHolderId(),
                commission.getAmount(),
                commission.getCurrency(),
                commission.getCommissionType(),
                commission.getStatus(),
                commission.getWeekId(),
                commission.getSource(),
                commission.getCreatedAt(),
                commission.getPaidAt()
        );
    }
}
