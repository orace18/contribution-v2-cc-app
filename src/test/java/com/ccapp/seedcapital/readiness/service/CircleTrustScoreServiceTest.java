package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.seedcapital.readiness.domain.entity.CircleTrustScore;
import com.ccapp.seedcapital.readiness.dto.CircleTrustScoreResponse;
import com.ccapp.seedcapital.readiness.repository.CircleTrustScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircleTrustScoreServiceTest {

    @Mock
    private CircleReadinessAccessService circleReadinessAccessService;
    @Mock
    private CircleMeetingService circleMeetingService;
    @Mock
    private CircleCoachAssignmentService circleCoachAssignmentService;
    @Mock
    private CircleTrustScoreRepository circleTrustScoreRepository;
    @Mock
    private com.ccapp.circlesocial.repository.CircleActivityRepository circleActivityRepository;
    @Mock
    private FinancialAccessPolicy financialAccessPolicy;

    @InjectMocks
    private CircleTrustScoreService circleTrustScoreService;

    @Test
    void recalculateScore_shouldCombineMembershipMeetingsActivityAndCoachFactors() {
        UUID circleId = UUID.randomUUID();
        CircleMember member1 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member2 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member3 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member4 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();

        when(circleReadinessAccessService.requireActiveMembers(circleId)).thenReturn(List.of(member1, member2, member3, member4));
        when(financialAccessPolicy.isAllowed(member1.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(financialAccessPolicy.isAllowed(member2.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(financialAccessPolicy.isAllowed(member3.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(true);
        when(financialAccessPolicy.isAllowed(member4.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS)).thenReturn(false);
        when(circleMeetingService.countCompletedMeetingsWithQuorumSince(eq(circleId), any(OffsetDateTime.class))).thenReturn(2L);
        when(circleActivityRepository.countByCircleIdAndCreatedAtAfter(eq(circleId), any(OffsetDateTime.class))).thenReturn(3L);
        when(circleCoachAssignmentService.hasAssignedCoach(circleId)).thenReturn(true);
        when(circleTrustScoreRepository.findById(circleId)).thenReturn(java.util.Optional.empty());
        when(circleTrustScoreRepository.save(any(CircleTrustScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CircleTrustScoreResponse response = circleTrustScoreService.recalculateScore(circleId);

        assertEquals(4, response.membersCount());
        assertEquals(3, response.financiallyReadyMembersCount());
        assertEquals(2, response.completedMeetingsCount());
        assertEquals(3, response.recentActivityCount());
        assertEquals("75.00", response.membershipComplianceScore().toPlainString());
        assertEquals("100.00", response.circleSizeScore().toPlainString());
        assertEquals("100.00", response.meetingsScore().toPlainString());
        assertEquals("60.00", response.activityScore().toPlainString());
        assertEquals("100.00", response.coachScore().toPlainString());
        assertEquals("87.25", response.globalScore().toPlainString());
    }
}
