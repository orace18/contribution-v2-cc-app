package com.ccapp.seedcapital.readiness.service;

import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.service.CreditScoringService;
import com.ccapp.financial.access.domain.FinancialAccessDecision;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalCreditCandidateResponse;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalEligibilityResponse;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedCapitalCreditBridgeServiceTest {

    @Mock
    private CircleReadinessAccessService circleReadinessAccessService;
    @Mock
    private FinancialAccessPolicy financialAccessPolicy;
    @Mock
    private SeedCapitalEligibilityService seedCapitalEligibilityService;
    @Mock
    private CreditScoringService creditScoringService;

    @InjectMocks
    private SeedCapitalCreditBridgeService seedCapitalCreditBridgeService;

    @Test
    void getCandidate_shouldStayWaitingWhileFutureGatesRemain() {
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        doNothing().when(circleReadinessAccessService).assertCircleMember(accountHolderId, circleId);
        when(financialAccessPolicy.evaluate(accountHolderId, FinancialAccessUseCase.CREDIT_APPLICATION)).thenReturn(
                new FinancialAccessDecision(
                        accountHolderId,
                        FinancialAccessUseCase.CREDIT_APPLICATION,
                        FinancialAccessState.FINANCIAL_ALLOWED,
                        true,
                        List.of(),
                        List.of(),
                        new MemberFinancialProfile(
                                accountHolderId,
                                AccountStatus.ACTIVE,
                                null,
                                MembershipStatus.ACTIVE,
                                OffsetDateTime.now().plusDays(30),
                                true,
                                "ACTIVE",
                                true,
                                false,
                                FinancialAccessState.FINANCIAL_ALLOWED,
                                false,
                                "NOT_CONFIGURED",
                                List.of()
                        )
                )
        );
        when(seedCapitalEligibilityService.getEligibility(accountHolderId, circleId)).thenReturn(
                new SeedCapitalEligibilityResponse(
                        circleId,
                        true,
                        SeedCapitalEligibilityState.READY_FOR_FUTURE_GATES,
                        new BigDecimal("88.00"),
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        ImfSavingsGateState.CHANNELS_MISSING,
                        0,
                        0,
                        0,
                        2,
                        List.of(),
                        List.of(),
                        List.of(
                                SeedCapitalPendingCriterion.IMF_CHANNEL_MISSING,
                                SeedCapitalPendingCriterion.SAVINGS_DISCIPLINE_INSUFFICIENT
                        ),
                        OffsetDateTime.now()
                )
        );
        when(creditScoringService.evaluateEligibility(accountHolderId, circleId)).thenReturn(
                new CreditEligibilityResponse(
                        accountHolderId,
                        circleId,
                        new BigDecimal("72.00"),
                        new BigDecimal("65.00"),
                        new BigDecimal("69.00"),
                        new BigDecimal("690000.0000"),
                        OffsetDateTime.now()
                )
        );

        SeedCapitalCreditCandidateResponse response = seedCapitalCreditBridgeService.getCandidate(accountHolderId, circleId);

        assertFalse(response.candidate());
        assertEquals(SeedCapitalCreditBridgeState.WAITING_FUTURE_GATES, response.state());
        assertEquals(List.of(
                SeedCapitalPendingCriterion.IMF_CHANNEL_MISSING,
                SeedCapitalPendingCriterion.SAVINGS_DISCIPLINE_INSUFFICIENT
        ), response.readinessPendingCriteria());
        assertEquals(ImfSavingsGateState.CHANNELS_MISSING, response.savingsGateState());
    }

    @Test
    void getCandidate_shouldReturnCandidateWhenReadinessAndCreditAreBothClear() {
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        doNothing().when(circleReadinessAccessService).assertCircleMember(accountHolderId, circleId);
        when(financialAccessPolicy.evaluate(accountHolderId, FinancialAccessUseCase.CREDIT_APPLICATION)).thenReturn(
                new FinancialAccessDecision(
                        accountHolderId,
                        FinancialAccessUseCase.CREDIT_APPLICATION,
                        FinancialAccessState.FINANCIAL_ALLOWED,
                        true,
                        List.of(),
                        List.of(),
                        new MemberFinancialProfile(
                                accountHolderId,
                                AccountStatus.ACTIVE,
                                null,
                                MembershipStatus.ACTIVE,
                                OffsetDateTime.now().plusDays(30),
                                true,
                                "ACTIVE",
                                true,
                                false,
                                FinancialAccessState.FINANCIAL_ALLOWED,
                                false,
                                "NOT_CONFIGURED",
                                List.of()
                        )
                )
        );
        when(seedCapitalEligibilityService.getEligibility(accountHolderId, circleId)).thenReturn(
                new SeedCapitalEligibilityResponse(
                        circleId,
                        true,
                        SeedCapitalEligibilityState.ELIGIBLE,
                        new BigDecimal("92.00"),
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        ImfSavingsGateState.READY,
                        3,
                        3,
                        2,
                        2,
                        List.of(),
                        List.of(),
                        List.of(),
                        OffsetDateTime.now()
                )
        );
        when(creditScoringService.evaluateEligibility(accountHolderId, circleId)).thenReturn(
                new CreditEligibilityResponse(
                        accountHolderId,
                        circleId,
                        new BigDecimal("78.00"),
                        new BigDecimal("81.00"),
                        new BigDecimal("80.00"),
                        new BigDecimal("800000.0000"),
                        OffsetDateTime.now()
                )
        );

        SeedCapitalCreditCandidateResponse response = seedCapitalCreditBridgeService.getCandidate(accountHolderId, circleId);

        assertTrue(response.candidate());
        assertEquals(SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION, response.state());
        assertEquals("800000.0000", response.maxEligibleAmount().toPlainString());
    }

    @Test
    void getCandidate_shouldBeBlockedByMemberAccessBeforeCircleReadiness() {
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        doNothing().when(circleReadinessAccessService).assertCircleMember(accountHolderId, circleId);
        when(financialAccessPolicy.evaluate(accountHolderId, FinancialAccessUseCase.CREDIT_APPLICATION)).thenReturn(
                new FinancialAccessDecision(
                        accountHolderId,
                        FinancialAccessUseCase.CREDIT_APPLICATION,
                        FinancialAccessState.FINANCIAL_DENIED,
                        false,
                        List.of(FinancialAccessReasonCode.MEMBERSHIP_EXPIRED),
                        List.of("Membership has expired for credit application"),
                        new MemberFinancialProfile(
                                accountHolderId,
                                AccountStatus.ACTIVE,
                                null,
                                MembershipStatus.EXPIRED,
                                OffsetDateTime.now().minusDays(1),
                                false,
                                "ACTIVE",
                                false,
                                false,
                                FinancialAccessState.FINANCIAL_DENIED,
                                false,
                                "NOT_CONFIGURED",
                                List.of(FinancialAccessReasonCode.MEMBERSHIP_EXPIRED)
                        )
                )
        );
        when(seedCapitalEligibilityService.getEligibility(accountHolderId, circleId)).thenReturn(
                new SeedCapitalEligibilityResponse(
                        circleId,
                        false,
                        SeedCapitalEligibilityState.BLOCKED,
                        new BigDecimal("52.00"),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        ImfSavingsGateState.CHANNELS_MISSING,
                        0,
                        0,
                        0,
                        2,
                        List.of(),
                        List.of(SeedCapitalBlockingReason.INVALID_CIRCLE_SIZE),
                        List.of(),
                        OffsetDateTime.now()
                )
        );
        when(creditScoringService.evaluateEligibility(accountHolderId, circleId)).thenReturn(
                new CreditEligibilityResponse(
                        accountHolderId,
                        circleId,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO.setScale(4),
                        OffsetDateTime.now()
                )
        );

        SeedCapitalCreditCandidateResponse response = seedCapitalCreditBridgeService.getCandidate(accountHolderId, circleId);

        assertFalse(response.candidate());
        assertEquals(SeedCapitalCreditBridgeState.BLOCKED_BY_MEMBER_ACCESS, response.state());
        assertEquals(List.of(FinancialAccessReasonCode.MEMBERSHIP_EXPIRED), response.memberAccessReasonCodes());
    }
}
