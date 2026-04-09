package com.ccapp.seedcapital.readiness.dto;

import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SeedCapitalCreditCandidateResponse(
        UUID accountHolderId,
        UUID circleId,
        boolean candidate,
        SeedCapitalCreditBridgeState state,
        FinancialAccessState memberAccessState,
        List<FinancialAccessReasonCode> memberAccessReasonCodes,
        SeedCapitalEligibilityState readinessState,
        BigDecimal readinessTrustScore,
        List<SeedCapitalBlockingReason> readinessBlockingReasons,
        List<SeedCapitalPendingCriterion> readinessPendingCriteria,
        ImfSavingsGateState savingsGateState,
        int configuredImfChannelsCount,
        int readyImfChannelsCount,
        int qualifyingSavingsCyclesCount,
        int requiredSavingsCyclesCount,
        BigDecimal creditEligibilityScore,
        BigDecimal maxEligibleAmount,
        OffsetDateTime evaluatedAt
) {
}
