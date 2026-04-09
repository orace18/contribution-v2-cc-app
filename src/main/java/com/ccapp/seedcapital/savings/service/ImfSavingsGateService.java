package com.ccapp.seedcapital.savings.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import com.ccapp.seedcapital.savings.dto.CircleSavingsGateResponse;
import com.ccapp.seedcapital.savings.repository.MemberSavingsContributionRepository;
import com.ccapp.seedcapital.savings.repository.SavingsCycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImfSavingsGateService {

    private static final int REQUIRED_QUALIFYING_CYCLES = 2;
    private static final long DISCIPLINE_WINDOW_DAYS = 90L;

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CountryContextService countryContextService;
    private final ImfSavingsChannelService imfSavingsChannelService;
    private final SavingsCycleService savingsCycleService;
    private final SavingsCycleRepository savingsCycleRepository;
    private final MemberSavingsContributionRepository memberSavingsContributionRepository;

    @Transactional
    public CircleSavingsGateResponse getGate(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        return evaluate(circleId);
    }

    @Transactional
    public CircleSavingsGateResponse evaluate(UUID circleId) {
        List<CircleMember> members = circleReadinessAccessService.requireActiveMembers(circleId);
        String countryCode = resolveCountryCode(members);
        Map<UUID, ImfSavingsChannel> channelsByMember = members.isEmpty()
                ? Map.of()
                : imfSavingsChannelService.findChannelsForCountry(
                        members.stream().map(CircleMember::getAccountHolderId).toList(),
                        countryCode
                );
        int configuredChannelsCount = channelsByMember.size();
        int readyChannelsCount = (int) channelsByMember.values().stream()
                .filter(imfSavingsChannelService::isReady)
                .count();

        savingsCycleService.refreshCycleStatuses(circleId);
        int qualifyingCyclesCount = countQualifyingCycles(circleId);

        ImfSavingsGateState state = resolveState(members.size(), configuredChannelsCount, readyChannelsCount, qualifyingCyclesCount);
        return new CircleSavingsGateResponse(
                circleId,
                countryCode,
                state,
                members.size(),
                configuredChannelsCount,
                readyChannelsCount,
                qualifyingCyclesCount,
                REQUIRED_QUALIFYING_CYCLES,
                OffsetDateTime.now()
        );
    }

    private int countQualifyingCycles(UUID circleId) {
        List<SavingsCycle> cycles = savingsCycleRepository.findByCircleIdAndStatusInAndWindowStartAfterOrderByWindowStartDesc(
                circleId,
                EnumSet.of(SavingsCycleStatus.COMPLETED),
                OffsetDateTime.now().minusDays(DISCIPLINE_WINDOW_DAYS)
        );
        return (int) cycles.stream()
                .filter(this::isQualifyingCycle)
                .count();
    }

    private boolean isQualifyingCycle(SavingsCycle cycle) {
        List<MemberSavingsContribution> contributions = memberSavingsContributionRepository.findByCycleIdOrderByCreatedAtAsc(cycle.getId());
        return contributions.size() >= cycle.getParticipantCount()
                && contributions.stream().allMatch(contribution ->
                        contribution.getStatus() == MemberSavingsContributionStatus.CONFIRMED
                                && contribution.getConfirmedAmount().compareTo(contribution.getExpectedAmount()) >= 0
                );
    }

    private ImfSavingsGateState resolveState(int membersCount,
                                             int configuredChannelsCount,
                                             int readyChannelsCount,
                                             int qualifyingCyclesCount) {
        if (membersCount == 0 || configuredChannelsCount < membersCount) {
            return ImfSavingsGateState.CHANNELS_MISSING;
        }
        if (readyChannelsCount < membersCount) {
            return ImfSavingsGateState.CHANNELS_NOT_READY;
        }
        if (qualifyingCyclesCount < REQUIRED_QUALIFYING_CYCLES) {
            return ImfSavingsGateState.DISCIPLINE_INSUFFICIENT;
        }
        return ImfSavingsGateState.READY;
    }

    private String resolveCountryCode(List<CircleMember> members) {
        if (members.isEmpty()) {
            return null;
        }
        CountryContext countryContext = countryContextService.resolve(members.getFirst().getAccountHolderId());
        return countryContext.countryCode();
    }
}
