package com.ccapp.mlm.service;

import com.ccapp.mlm.domain.entity.MlmMemberMetrics;
import com.ccapp.mlm.domain.entity.MlmWeeklySnapshot;
import com.ccapp.mlm.graph.MlmGraphService;
import com.ccapp.mlm.repository.MlmMemberMetricsRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.MlmWeeklySnapshotRepository;
import com.ccapp.mlm.repository.projection.MlmLevelBreakdownProjection;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MlmAnalyticsService {

    private final MlmMemberMetricsRepository mlmMemberMetricsRepository;
    private final MlmWeeklySnapshotRepository mlmWeeklySnapshotRepository;
    private final MlmGraphService mlmGraphService;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    private final MlmCommissionRepository mlmCommissionRepository;

    @Value("${mlm.analytics.active-window-days:30}")
    private int activeWindowDays;

    @Value("${mlm.analytics.zone:Africa/Porto-Novo}")
    private String analyticsZone;

    @Transactional
    public MlmMemberMetrics refreshMemberMetrics(UUID accountHolderId) {
        long teamSize = mlmGraphService.countDownline(accountHolderId);
        OffsetDateTime activeFrom = OffsetDateTime.now(ZoneId.of(analyticsZone)).minusDays(activeWindowDays);
        long activeTeamMembers = mlmVolumeLedgerRepository.countDistinctSourceMembersByAccountHolderIdSince(accountHolderId, activeFrom);
        BigDecimal totalVolume = safeAmount(mlmVolumeLedgerRepository.sumVolumeByAccountHolderId(accountHolderId));
        BigDecimal totalCommissions = safeAmount(mlmCommissionRepository.sumAmountByBeneficiaryAccountHolderId(accountHolderId));

        MlmMemberMetrics metrics = mlmMemberMetricsRepository.findById(accountHolderId)
                .orElseGet(() -> MlmMemberMetrics.builder().accountHolderId(accountHolderId).build());
        metrics.setTeamSize(teamSize);
        metrics.setActiveTeamMembers(activeTeamMembers);
        metrics.setTotalVolume(totalVolume);
        metrics.setTotalCommissions(totalCommissions);
        return mlmMemberMetricsRepository.save(metrics);
    }

    @Transactional
    public MlmMemberMetrics getOrRefreshMemberMetrics(UUID accountHolderId) {
        return mlmMemberMetricsRepository.findById(accountHolderId)
                .orElseGet(() -> refreshMemberMetrics(accountHolderId));
    }

    @Transactional
    public int captureCurrentWeekSnapshot() {
        return captureWeekSnapshot(currentWeekId());
    }

    @Transactional
    public int captureWeekSnapshot(int weekId) {
        Set<UUID> members = new HashSet<>();
        members.addAll(mlmVolumeLedgerRepository.findDistinctAccountHolderIdsByWeekId(weekId));
        members.addAll(mlmCommissionRepository.findDistinctBeneficiaryAccountHolderIdsByWeekIdAndCommissionType(
                weekId, CommissionType.GROUP_BONUS));
        if (members.isEmpty()) {
            return 0;
        }

        for (UUID accountHolderId : members) {
            BigDecimal teamVolume = safeAmount(mlmVolumeLedgerRepository.sumVolumeByAccountHolderIdAndWeekId(accountHolderId, weekId));
            long teamMembers = mlmVolumeLedgerRepository.countDistinctSourceMembersByAccountHolderIdAndWeekId(accountHolderId, weekId);
            BigDecimal commissionsGenerated = safeAmount(mlmCommissionRepository
                    .sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(
                            accountHolderId, weekId, CommissionType.GROUP_BONUS));
            BigDecimal commissionsPaid = safeAmount(mlmCommissionRepository
                    .sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionTypeAndStatus(
                            accountHolderId, weekId, CommissionType.GROUP_BONUS, CommissionStatus.PAID));

            MlmWeeklySnapshot snapshot = mlmWeeklySnapshotRepository.findByWeekIdAndAccountHolderId(weekId, accountHolderId)
                    .orElseGet(() -> MlmWeeklySnapshot.builder()
                            .weekId(weekId)
                            .accountHolderId(accountHolderId)
                            .build());
            snapshot.setTeamVolume(teamVolume);
            snapshot.setTeamMembers(teamMembers);
            snapshot.setCommissionsGenerated(commissionsGenerated);
            snapshot.setCommissionsPaid(commissionsPaid);
            mlmWeeklySnapshotRepository.save(snapshot);
        }
        return members.size();
    }

    @Transactional(readOnly = true)
    public List<MlmLevelBreakdownProjection> levelBreakdown(UUID accountHolderId) {
        Map<Integer, Long> membersByLevel = new HashMap<>();
        for (MlmGraphService.GraphMember member : mlmGraphService.getDownline(accountHolderId, Integer.MAX_VALUE, Long.MAX_VALUE)) {
            if (member.level() <= 0) {
                continue;
            }
            membersByLevel.merge(member.level(), 1L, Long::sum);
        }
        return membersByLevel.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(entry -> new LevelBreakdown(entry.getKey(), entry.getValue()))
                .map(MlmLevelBreakdownProjection.class::cast)
                .toList();
    }

    @Transactional(readOnly = true)
    public int currentWeekId() {
        LocalDate localDate = LocalDate.now(ZoneId.of(analyticsZone));
        int weekYear = localDate.get(IsoFields.WEEK_BASED_YEAR);
        int week = localDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (weekYear * 100) + week;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private record LevelBreakdown(Integer level, Long members) implements MlmLevelBreakdownProjection {
        @Override
        public Integer getLevel() {
            return level;
        }

        @Override
        public Long getMembers() {
            return members;
        }
    }
}
