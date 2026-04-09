package com.ccapp.seedcapital.readiness.dto;

import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalSatisfiedCriterion;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SeedCapitalEligibilityResponse(
        UUID circleId,
        boolean eligible,
        SeedCapitalEligibilityState state,
        BigDecimal trustScore,
        boolean validCircleSize,
        boolean membershipCompliant,
        boolean meetingsRequirementMet,
        boolean coachAssigned,
        boolean trustScoreThresholdMet,
        boolean imfChannelCoverageMet,
        boolean savingsDisciplineMet,
        ImfSavingsGateState savingsGateState,
        int configuredImfChannelsCount,
        int readyImfChannelsCount,
        int qualifyingSavingsCyclesCount,
        int requiredSavingsCyclesCount,
        List<SeedCapitalSatisfiedCriterion> satisfiedCriteria,
        List<SeedCapitalBlockingReason> blockingReasons,
        List<SeedCapitalPendingCriterion> pendingCriteria,
        OffsetDateTime evaluatedAt
) {
}
