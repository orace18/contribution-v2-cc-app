package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.mlm.domain.entity.MlmMemberGradeProfile;
import com.ccapp.mlm.domain.entity.MlmMemberMetrics;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.enums.MlmMemberGrade;
import com.ccapp.mlm.repository.MlmMemberGradeRepository;
import com.ccapp.mlm.repository.MlmMemberMetricsRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircleNetworkCoachingServiceTest {

    @Mock
    private CircleRepository circleRepository;
    @Mock
    private CircleMemberRepository circleMemberRepository;
    @Mock
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Mock
    private MlmMemberGradeRepository mlmMemberGradeRepository;
    @Mock
    private MlmMemberMetricsRepository mlmMemberMetricsRepository;

    @InjectMocks
    private CircleNetworkCoachingService circleNetworkCoachingService;

    @Test
    void resolveSnapshot_shouldResolveInheritedCoachAndBoundedNetworkSignal() {
        UUID circleId = UUID.randomUUID();
        UUID member1Id = UUID.randomUUID();
        UUID member2Id = UUID.randomUUID();
        UUID member3Id = UUID.randomUUID();
        UUID coachId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        when(circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE))
                .thenReturn(Optional.of(Circle.builder().id(circleId).status(CircleStatus.ACTIVE).build()));
        when(circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId)).thenReturn(List.of(
                CircleMember.builder().accountHolderId(member1Id).build(),
                CircleMember.builder().accountHolderId(member2Id).build(),
                CircleMember.builder().accountHolderId(member3Id).build()
        ));
        when(mlmRelationshipRepository.findAncestors(member1Id)).thenReturn(List.of(
                MlmRelationship.builder().ancestorId(member1Id).descendantId(member1Id).level(0).createdAt(OffsetDateTime.now().minusDays(12)).build(),
                MlmRelationship.builder().ancestorId(coachId).descendantId(member1Id).level(1).createdAt(OffsetDateTime.now().minusDays(11)).build(),
                MlmRelationship.builder().ancestorId(advisorId).descendantId(member1Id).level(2).createdAt(OffsetDateTime.now().minusDays(10)).build()
        ));
        when(mlmRelationshipRepository.findAncestors(member2Id)).thenReturn(List.of(
                MlmRelationship.builder().ancestorId(member2Id).descendantId(member2Id).level(0).createdAt(OffsetDateTime.now().minusDays(12)).build(),
                MlmRelationship.builder().ancestorId(coachId).descendantId(member2Id).level(1).createdAt(OffsetDateTime.now().minusDays(9)).build(),
                MlmRelationship.builder().ancestorId(advisorId).descendantId(member2Id).level(2).createdAt(OffsetDateTime.now().minusDays(8)).build()
        ));
        when(mlmRelationshipRepository.findAncestors(member3Id)).thenReturn(List.of(
                MlmRelationship.builder().ancestorId(member3Id).descendantId(member3Id).level(0).createdAt(OffsetDateTime.now().minusDays(12)).build(),
                MlmRelationship.builder().ancestorId(coachId).descendantId(member3Id).level(2).createdAt(OffsetDateTime.now().minusDays(7)).build(),
                MlmRelationship.builder().ancestorId(advisorId).descendantId(member3Id).level(3).createdAt(OffsetDateTime.now().minusDays(6)).build()
        ));
        when(mlmMemberGradeRepository.findById(coachId)).thenReturn(Optional.of(
                MlmMemberGradeProfile.builder().memberId(coachId).grade(MlmMemberGrade.GOLD).build()
        ));
        when(mlmMemberMetricsRepository.findById(coachId)).thenReturn(Optional.of(
                MlmMemberMetrics.builder().accountHolderId(coachId).teamSize(5L).build()
        ));

        CircleNetworkCoachingService.NetworkCoachingSnapshot snapshot = circleNetworkCoachingService.resolveSnapshot(circleId);

        assertTrue(snapshot.coachResolved());
        assertEquals(coachId, snapshot.coachAccountHolderId());
        assertEquals(List.of(advisorId), snapshot.advisorAccountHolderIds());
        assertEquals(3, snapshot.lineageCoverageCount());
        assertEquals(2, snapshot.coachLevel());
        assertEquals("100.00", snapshot.lineageCoverageScore().toPlainString());
        assertEquals("GOLD", snapshot.coachGrade());
        assertEquals(5L, snapshot.coachTeamSize());
        assertEquals("86.50", snapshot.networkSignalScore().toPlainString());
    }
}
