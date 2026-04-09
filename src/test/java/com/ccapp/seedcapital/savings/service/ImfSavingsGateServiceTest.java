package com.ccapp.seedcapital.savings.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsChannelStatus;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import com.ccapp.seedcapital.savings.dto.CircleSavingsGateResponse;
import com.ccapp.seedcapital.savings.repository.MemberSavingsContributionRepository;
import com.ccapp.seedcapital.savings.repository.SavingsCycleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImfSavingsGateServiceTest {

    @Mock
    private CircleReadinessAccessService circleReadinessAccessService;
    @Mock
    private CountryContextService countryContextService;
    @Mock
    private ImfSavingsChannelService imfSavingsChannelService;
    @Mock
    private SavingsCycleService savingsCycleService;
    @Mock
    private SavingsCycleRepository savingsCycleRepository;
    @Mock
    private MemberSavingsContributionRepository memberSavingsContributionRepository;

    @InjectMocks
    private ImfSavingsGateService imfSavingsGateService;

    @Test
    void evaluate_shouldReturnReadyWhenAllChannelsAndCyclesQualify() {
        UUID circleId = UUID.randomUUID();
        CircleMember member1 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member2 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member3 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        List<CircleMember> members = List.of(member1, member2, member3);

        when(circleReadinessAccessService.requireActiveMembers(circleId)).thenReturn(members);
        when(countryContextService.resolve(member1.getAccountHolderId())).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(imfSavingsChannelService.findChannelsForCountry(List.of(member1.getAccountHolderId(), member2.getAccountHolderId(), member3.getAccountHolderId()), "BJ"))
                .thenReturn(Map.of(
                        member1.getAccountHolderId(), activeChannel(member1.getAccountHolderId()),
                        member2.getAccountHolderId(), activeChannel(member2.getAccountHolderId()),
                        member3.getAccountHolderId(), activeChannel(member3.getAccountHolderId())
                ));
        when(imfSavingsChannelService.isReady(org.mockito.ArgumentMatchers.any(ImfSavingsChannel.class))).thenReturn(true);
        doNothing().when(savingsCycleService).refreshCycleStatuses(circleId);

        SavingsCycle cycle1 = completedCycle(circleId, 1);
        SavingsCycle cycle2 = completedCycle(circleId, 2);
        when(savingsCycleRepository.findByCircleIdAndStatusInAndWindowStartAfterOrderByWindowStartDesc(
                org.mockito.ArgumentMatchers.eq(circleId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        )).thenReturn(List.of(cycle1, cycle2));
        when(memberSavingsContributionRepository.findByCycleIdOrderByCreatedAtAsc(cycle1.getId()))
                .thenReturn(confirmedContributions(cycle1.getId(), members));
        when(memberSavingsContributionRepository.findByCycleIdOrderByCreatedAtAsc(cycle2.getId()))
                .thenReturn(confirmedContributions(cycle2.getId(), members));

        CircleSavingsGateResponse response = imfSavingsGateService.evaluate(circleId);

        assertEquals(ImfSavingsGateState.READY, response.state());
        assertEquals(3, response.readyChannelsCount());
        assertEquals(2, response.qualifyingSavingsCyclesCount());
    }

    @Test
    void evaluate_shouldReturnChannelsMissingWhenSomeMembersHaveNoChannel() {
        UUID circleId = UUID.randomUUID();
        CircleMember member1 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member2 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        CircleMember member3 = CircleMember.builder().accountHolderId(UUID.randomUUID()).build();
        List<CircleMember> members = List.of(member1, member2, member3);

        when(circleReadinessAccessService.requireActiveMembers(circleId)).thenReturn(members);
        when(countryContextService.resolve(member1.getAccountHolderId())).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(imfSavingsChannelService.findChannelsForCountry(List.of(member1.getAccountHolderId(), member2.getAccountHolderId(), member3.getAccountHolderId()), "BJ"))
                .thenReturn(Map.of(
                        member1.getAccountHolderId(), activeChannel(member1.getAccountHolderId()),
                        member2.getAccountHolderId(), activeChannel(member2.getAccountHolderId())
                ));
        when(imfSavingsChannelService.isReady(org.mockito.ArgumentMatchers.any(ImfSavingsChannel.class))).thenReturn(true);
        doNothing().when(savingsCycleService).refreshCycleStatuses(circleId);
        when(savingsCycleRepository.findByCircleIdAndStatusInAndWindowStartAfterOrderByWindowStartDesc(
                org.mockito.ArgumentMatchers.eq(circleId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        )).thenReturn(List.of());

        CircleSavingsGateResponse response = imfSavingsGateService.evaluate(circleId);

        assertEquals(ImfSavingsGateState.CHANNELS_MISSING, response.state());
        assertEquals(2, response.configuredChannelsCount());
        assertEquals(0, response.qualifyingSavingsCyclesCount());
    }

    private ImfSavingsChannel activeChannel(UUID accountHolderId) {
        return ImfSavingsChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .partnerId(UUID.randomUUID())
                .countryCode("BJ")
                .status(ImfSavingsChannelStatus.ACTIVE)
                .build();
    }

    private SavingsCycle completedCycle(UUID circleId, int cycleNumber) {
        return SavingsCycle.builder()
                .id(UUID.randomUUID())
                .circleId(circleId)
                .cycleNumber(cycleNumber)
                .windowStart(OffsetDateTime.now().minusDays(20L * cycleNumber))
                .windowEnd(OffsetDateTime.now().minusDays((20L * cycleNumber) - 7))
                .expectedAmountPerMember(new BigDecimal("10000.0000"))
                .countryCode("BJ")
                .currency("XOF")
                .participantCount(3)
                .status(SavingsCycleStatus.COMPLETED)
                .build();
    }

    private List<MemberSavingsContribution> confirmedContributions(UUID cycleId, List<CircleMember> members) {
        return members.stream()
                .map(member -> MemberSavingsContribution.builder()
                        .id(UUID.randomUUID())
                        .cycleId(cycleId)
                        .accountHolderId(member.getAccountHolderId())
                        .expectedAmount(new BigDecimal("10000.0000"))
                        .confirmedAmount(new BigDecimal("10000.0000"))
                        .status(MemberSavingsContributionStatus.CONFIRMED)
                        .build())
                .toList();
    }
}
