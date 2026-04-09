package com.ccapp.credit.application.dto;

import com.ccapp.credit.application.domain.enums.CreditApplicationProductType;
import com.ccapp.credit.application.domain.enums.CreditApplicationStatus;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreditApplicationResponse(
        UUID id,
        UUID accountHolderId,
        UUID circleId,
        CreditApplicationProductType productType,
        SeedCapitalCreditBridgeState bridgeState,
        CreditApplicationStatus workflowState,
        BigDecimal requestedAmount,
        String currency,
        Integer requestedDurationMonths,
        BigDecimal requestedInterestRate,
        SeedCapitalEligibilityState readinessState,
        BigDecimal readinessTrustScore,
        BigDecimal creditEligibilityScore,
        BigDecimal maxEligibleAmount,
        ImfSavingsGateState savingsGateState,
        int configuredImfChannelsCount,
        int readyImfChannelsCount,
        int qualifyingSavingsCyclesCount,
        int requiredSavingsCyclesCount,
        List<SeedCapitalBlockingReason> blockingReasons,
        List<SeedCapitalPendingCriterion> pendingCriteria,
        UUID linkedLoanId,
        OffsetDateTime submittedAt,
        OffsetDateTime bridgeEvaluatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
