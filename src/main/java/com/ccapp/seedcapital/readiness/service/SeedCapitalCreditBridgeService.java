package com.ccapp.seedcapital.readiness.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.service.CreditScoringService;
import com.ccapp.financial.access.domain.FinancialAccessDecision;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.service.KycStatusConsistencyService;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalCreditCandidateResponse;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalEligibilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeedCapitalCreditBridgeService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final FinancialAccessPolicy financialAccessPolicy;
    private final SeedCapitalEligibilityService seedCapitalEligibilityService;
    private final CreditScoringService creditScoringService;
    private final KycStatusConsistencyService kycStatusConsistencyService;

    @Transactional
    public SeedCapitalCreditCandidateResponse getCandidate(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCircleMember(requesterAccountHolderId, circleId);

        FinancialAccessDecision memberAccessDecision = applyKycRequirement(
                requesterAccountHolderId,
                financialAccessPolicy.evaluate(
                        requesterAccountHolderId,
                        FinancialAccessUseCase.CREDIT_APPLICATION
                )
        );
        SeedCapitalEligibilityResponse readiness = seedCapitalEligibilityService.getEligibility(requesterAccountHolderId, circleId);
        CreditEligibilityResponse creditEligibility = creditScoringService.evaluateEligibility(requesterAccountHolderId, circleId);

        SeedCapitalCreditBridgeState state = resolveState(memberAccessDecision, readiness, creditEligibility);
        boolean candidate = state == SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION;

        return new SeedCapitalCreditCandidateResponse(
                requesterAccountHolderId,
                circleId,
                candidate,
                state,
                memberAccessDecision.state(),
                memberAccessDecision.reasonCodes(),
                readiness.state(),
                readiness.trustScore(),
                readiness.blockingReasons(),
                readiness.pendingCriteria(),
                readiness.savingsGateState(),
                readiness.configuredImfChannelsCount(),
                readiness.readyImfChannelsCount(),
                readiness.qualifyingSavingsCyclesCount(),
                readiness.requiredSavingsCyclesCount(),
                creditEligibility.eligibilityScore(),
                creditEligibility.maxEligibleAmount(),
                OffsetDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public SeedCapitalCreditCandidateResponse requireCandidate(UUID requesterAccountHolderId, UUID circleId) {
        SeedCapitalCreditCandidateResponse candidate = getCandidate(requesterAccountHolderId, circleId);
        if (!candidate.candidate()) {
            throw new BusinessException(blockingMessage(candidate));
        }
        return candidate;
    }

    private FinancialAccessDecision applyKycRequirement(UUID requesterAccountHolderId,
                                                        FinancialAccessDecision currentDecision) {
        if (!currentDecision.allowed()
                || kycStatusConsistencyService.reconcileAccountHolderStatus(requesterAccountHolderId) == KycStatus.VERIFIED) {
            return currentDecision;
        }

        return new FinancialAccessDecision(
                requesterAccountHolderId,
                currentDecision.useCase(),
                FinancialAccessState.FINANCIAL_DENIED,
                false,
                List.of(FinancialAccessReasonCode.KYC_NOT_VERIFIED),
                List.of("KYC must be verified for credit application"),
                currentDecision.profile()
        );
    }

    private SeedCapitalCreditBridgeState resolveState(FinancialAccessDecision memberAccessDecision,
                                                      SeedCapitalEligibilityResponse readiness,
                                                      CreditEligibilityResponse creditEligibility) {
        if (!memberAccessDecision.allowed()) {
            return SeedCapitalCreditBridgeState.BLOCKED_BY_MEMBER_ACCESS;
        }
        if (!readiness.blockingReasons().isEmpty()) {
            return SeedCapitalCreditBridgeState.BLOCKED_BY_CIRCLE_READINESS;
        }
        if (!readiness.pendingCriteria().isEmpty()) {
            return SeedCapitalCreditBridgeState.WAITING_FUTURE_GATES;
        }
        if (creditEligibility.maxEligibleAmount() == null
                || creditEligibility.maxEligibleAmount().compareTo(ZERO) <= 0) {
            return SeedCapitalCreditBridgeState.BLOCKED_BY_CREDIT_SCORING;
        }
        return SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION;
    }

    private String blockingMessage(SeedCapitalCreditCandidateResponse candidate) {
        return switch (candidate.state()) {
            case BLOCKED_BY_MEMBER_ACCESS ->
                    "Credit application blocked by member financial access: " + candidate.memberAccessReasonCodes();
            case BLOCKED_BY_CIRCLE_READINESS ->
                    "Credit application blocked by circle readiness: " + candidate.readinessBlockingReasons();
            case WAITING_FUTURE_GATES ->
                    "Credit application is waiting future gates: " + candidate.readinessPendingCriteria();
            case BLOCKED_BY_CREDIT_SCORING ->
                    "Credit application blocked by credit scoring";
            case CANDIDATE_FOR_CREDIT_APPLICATION ->
                    "Credit application candidate accepted";
        };
    }
}
