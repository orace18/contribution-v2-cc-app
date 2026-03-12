package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.domain.entity.MlmMemberMetrics;
import com.ccapp.mlm.dto.MobileMlmDashboardResponse;
import com.ccapp.mlm.dto.MobileMlmLevelBreakdownResponse;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileMlmDashboardService {

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final MlmAnalyticsService mlmAnalyticsService;
    private final MlmCommissionRepository mlmCommissionRepository;

    @Transactional
    public MobileMlmDashboardResponse getDashboard(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);

        MlmMemberMetrics metrics = mlmAnalyticsService.getOrRefreshMemberMetrics(accountHolderId);
        int currentWeekId = mlmAnalyticsService.currentWeekId();

        BigDecimal weeklyBonus = mlmCommissionRepository
                .sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(
                        accountHolderId,
                        currentWeekId,
                        CommissionType.GROUP_BONUS
                );

        List<MobileMlmLevelBreakdownResponse> levelBreakdown = mlmAnalyticsService.levelBreakdown(accountHolderId).stream()
                .map(projection -> new MobileMlmLevelBreakdownResponse(
                        projection.getLevel(),
                        projection.getMembers()
                ))
                .toList();

        return new MobileMlmDashboardResponse(
                metrics.getTeamSize(),
                metrics.getActiveTeamMembers(),
                metrics.getTotalVolume(),
                metrics.getTotalCommissions(),
                weeklyBonus == null ? BigDecimal.ZERO : weeklyBonus,
                levelBreakdown
        );
    }
}
