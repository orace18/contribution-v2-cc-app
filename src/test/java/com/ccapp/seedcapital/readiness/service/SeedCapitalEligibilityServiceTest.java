package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.seedcapital.readiness.domain.entity.SeedCapitalEligibility;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.readiness.dto.CircleTrustScoreResponse;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalEligibilityResponse;
import com.ccapp.seedcapital.readiness.repository.SeedCapitalEligibilityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedCapitalEligibilityServiceTest {

    @Mock
    private CircleReadinessAccessService circleReadinessAccessService;
    @Mock
    private CircleMeetingService circleMeetingService;
    @Mock
    private CircleCoachAssignmentService circleCoachAssignmentService;
    @Mock
    private CircleTrustScoreService circleTrustScoreService;
    @Mock
    private SeedCapitalEligibilityRepository seedCapitalEligibilityRepository;
    @Mock
    private FinancialAccessPolicy financialAccessPolicy;

    @InjectMocks
    private SeedCapitalEligibilityService seedCapitalEligibilityService;

    @Test
    void evaluate_shouldReturnReadyForFutureGatesWhenCurrentCriteriaPass() {
        UUID circleId = UUID.randomUUID();
        CircleMember member1 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member2 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member3 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();

        when(circleReadinessAccessService.requireActiveMembers(circleId)).thenReturn(List.of(member1, member2, member3));
        when(financialAccessPolicy.isAllowed(member1.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(financialAccessPolicy.isAllowed(member2.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(financialAccessPolicy.isAllowed(member3.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(circleMeetingService.countCompletedMeetingsWithQuorumSince(eq(circleId), any(OffsetDateTime.class))).thenReturn(2L);
        when(circleCoachAssignmentService.hasAssignedCoach(circleId)).thenReturn(true);
        when(circleTrustScoreService.getScoreForEvaluation(circleId)).thenReturn(new CircleTrustScoreResponse(
                circleId,
                3,
                3,
                2,
                4,
                true,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("4.00"),
                1,
                2,
                new BigDecimal("88.00"),
                OffsetDateTime.now()
        ));
        when(seedCapitalEligibilityRepository.findById(circleId)).thenReturn(Optional.empty());
        when(seedCapitalEligibilityRepository.save(any(SeedCapitalEligibility.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeedCapitalEligibilityResponse response = seedCapitalEligibilityService.evaluate(circleId);

        assertTrue(response.eligible());
        assertEquals(SeedCapitalEligibilityState.READY_FOR_FUTURE_GATES, response.state());
        assertTrue(response.blockingReasons().isEmpty());
        assertEquals(List.of(
                SeedCapitalPendingCriterion.IMF_CHANNEL_ACTIVE,
                SeedCapitalPendingCriterion.SAVINGS_DISCIPLINE_PROVEN
        ), response.pendingCriteria());
    }

    @Test
    void evaluate_shouldReturnBlockedReasonsWhenCurrentCriteriaFail() {
        UUID circleId = UUID.randomUUID();
        CircleMember member1 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member2 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();

        when(circleReadinessAccessService.requireActiveMembers(circleId)).thenReturn(List.of(member1, member2));
        when(financialAccessPolicy.isAllowed(member1.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(financialAccessPolicy.isAllowed(member2.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(false);
        when(circleMeetingService.countCompletedMeetingsWithQuorumSince(eq(circleId), any(OffsetDateTime.class))).thenReturn(0L);
        when(circleCoachAssignmentService.hasAssignedCoach(circleId)).thenReturn(false);
        when(circleTrustScoreService.getScoreForEvaluation(circleId)).thenReturn(new CircleTrustScoreResponse(
                circleId,
                2,
                1,
                0,
                1,
                false,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                null,
                new BigDecimal("21.50"),
                OffsetDateTime.now()
        ));
        when(seedCapitalEligibilityRepository.findById(circleId)).thenReturn(Optional.empty());
        when(seedCapitalEligibilityRepository.save(any(SeedCapitalEligibility.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeedCapitalEligibilityResponse response = seedCapitalEligibilityService.evaluate(circleId);

        assertEquals(false, response.eligible());
        assertEquals(SeedCapitalEligibilityState.BLOCKED, response.state());
        assertTrue(response.blockingReasons().contains(SeedCapitalBlockingReason.INVALID_CIRCLE_SIZE));
        assertTrue(response.blockingReasons().contains(SeedCapitalBlockingReason.MEMBERSHIP_NON_COMPLIANT));
        assertTrue(response.blockingReasons().contains(SeedCapitalBlockingReason.MEETINGS_NOT_REGULAR));
        assertTrue(response.blockingReasons().contains(SeedCapitalBlockingReason.COACH_NOT_ASSIGNED));
        assertTrue(response.blockingReasons().contains(SeedCapitalBlockingReason.TRUST_SCORE_BELOW_THRESHOLD));
    }
}
