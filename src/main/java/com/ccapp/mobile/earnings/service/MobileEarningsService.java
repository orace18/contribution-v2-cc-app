package com.ccapp.mobile.earnings.service;

import com.ccapp.credit.domain.entity.CreditEligibility;
import com.ccapp.credit.repository.CreditEligibilityRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.earnings.dto.MobileEarningHistoryItemResponse;
import com.ccapp.mobile.earnings.dto.MobileEarningsSummaryResponse;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileEarningsService {

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final CreditEligibilityRepository creditEligibilityRepository;

    @Value("${mobile.network.zone:Africa/Porto-Novo}")
    private String networkZone;

    @Transactional(readOnly = true)
    public MobileEarningsSummaryResponse getSummary(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);

        BigDecimal totalRms = safeAmount(mlmCommissionRepository.sumAmountByBeneficiaryAccountHolderId(accountHolderId));
        BigDecimal weeklyGrowth = safeAmount(mlmCommissionRepository.sumAmountByBeneficiaryAccountHolderIdSince(
                accountHolderId,
                startOfWeek()
        ));

        BigDecimal potential = creditEligibilityRepository.findById(accountHolderId)
                .map(CreditEligibility::getMaxEligibleAmount)
                .map(this::safeAmount)
                .orElseGet(() -> totalRms.multiply(BigDecimal.TEN));

        return new MobileEarningsSummaryResponse(totalRms, weeklyGrowth, potential);
    }

    @Transactional(readOnly = true)
    public List<MobileEarningHistoryItemResponse> getHistory(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);

        return mlmCommissionRepository.findByBeneficiaryAccountHolderIdOrderByCreatedAtDesc(accountHolderId).stream()
                .map(commission -> new MobileEarningHistoryItemResponse(
                        safeAmount(commission.getAmount()),
                        mapType(commission),
                        commission.getStatus(),
                        commission.getPaidAt() != null ? commission.getPaidAt() : commission.getCreatedAt()
                ))
                .toList();
    }

    private void assertViewAccess(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private OffsetDateTime startOfWeek() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of(networkZone));
        return now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay()
                .atOffset(now.getOffset());
    }

    private String mapType(MlmCommission commission) {
        if (commission == null || commission.getCommissionType() == null) {
            return "COMMISSION";
        }

        if (commission.getCommissionType() == CommissionType.GROUP_BONUS) {
            return "TEAM_BONUS";
        }

        if (commission.getCommissionType() == CommissionType.SUBSCRIPTION) {
            Integer generation = commission.getGeneration();
            if (generation != null && generation > 0) {
                return "LEVEL_" + generation + "_COMMISSION";
            }
            return "DIRECT_COMMISSION";
        }

        String source = commission.getSource() == null ? "" : commission.getSource().trim().toUpperCase(Locale.ROOT);
        if (source.contains("CIRCLE")) {
            return "CIRCLE_COMMISSION";
        }

        return commission.getCommissionType().name();
    }
}
