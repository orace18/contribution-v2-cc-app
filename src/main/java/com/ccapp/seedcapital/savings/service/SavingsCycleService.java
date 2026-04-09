package com.ccapp.seedcapital.savings.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import com.ccapp.seedcapital.savings.dto.CreateSavingsCycleRequest;
import com.ccapp.seedcapital.savings.dto.SavingsCycleResponse;
import com.ccapp.seedcapital.savings.repository.MemberSavingsContributionRepository;
import com.ccapp.seedcapital.savings.repository.SavingsCycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavingsCycleService {

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CountryContextService countryContextService;
    private final SavingsCycleRepository savingsCycleRepository;
    private final MemberSavingsContributionRepository memberSavingsContributionRepository;

    @Transactional
    public SavingsCycleResponse createCycle(UUID requesterAccountHolderId, UUID circleId, CreateSavingsCycleRequest request) {
        circleReadinessAccessService.assertCanManageReadiness(requesterAccountHolderId, circleId);
        List<CircleMember> members = circleReadinessAccessService.requireActiveMembers(circleId);
        if (members.isEmpty()) {
            throw new BusinessException("Cannot create a savings cycle without active circle members");
        }
        if (!request.windowEnd().isAfter(request.windowStart())) {
            throw new BusinessException("Savings cycle end must be after start");
        }

        CountryContext countryContext = countryContextService.resolve(members.getFirst().getAccountHolderId());
        SavingsCycle cycle = savingsCycleRepository.save(SavingsCycle.builder()
                .circleId(circleId)
                .createdByAccountHolderId(requesterAccountHolderId)
                .cycleNumber(Math.toIntExact(savingsCycleRepository.countByCircleId(circleId)) + 1)
                .windowStart(request.windowStart())
                .windowEnd(request.windowEnd())
                .expectedAmountPerMember(request.expectedAmountPerMember())
                .countryCode(countryContext.countryCode())
                .currency(countryContext.currency())
                .participantCount(members.size())
                .status(resolveInitialStatus(request.windowStart(), request.windowEnd()))
                .note(normalizeOptionalText(request.note()))
                .build());

        List<MemberSavingsContribution> contributions = members.stream()
                .map(member -> MemberSavingsContribution.builder()
                        .cycleId(cycle.getId())
                        .accountHolderId(member.getAccountHolderId())
                        .expectedAmount(request.expectedAmountPerMember())
                        .status(MemberSavingsContributionStatus.EXPECTED)
                        .build())
                .toList();
        memberSavingsContributionRepository.saveAll(contributions);

        return toResponse(cycle);
    }

    @Transactional
    public List<SavingsCycleResponse> listCycles(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        refreshCycleStatuses(circleId);
        return savingsCycleRepository.findByCircleIdOrderByWindowStartDesc(circleId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void refreshCycleStatuses(UUID circleId) {
        circleReadinessAccessService.requireActiveCircle(circleId);
        savingsCycleRepository.findByCircleIdOrderByWindowStartDesc(circleId)
                .forEach(this::refreshCycleStatus);
    }

    @Transactional(readOnly = true)
    public SavingsCycle requireCycle(UUID circleId, UUID cycleId) {
        SavingsCycle cycle = savingsCycleRepository.findById(cycleId)
                .orElseThrow(() -> new BusinessException("Savings cycle not found"));
        if (!cycle.getCircleId().equals(circleId)) {
            throw new BusinessException("Savings cycle does not belong to the selected circle");
        }
        return cycle;
    }

    @Transactional
    public SavingsCycle refreshCycleStatus(SavingsCycle cycle) {
        List<MemberSavingsContribution> contributions = memberSavingsContributionRepository.findByCycleIdOrderByCreatedAtAsc(cycle.getId());
        boolean allConfirmed = !contributions.isEmpty()
                && contributions.size() >= cycle.getParticipantCount()
                && contributions.stream().allMatch(contribution -> contribution.getStatus() == MemberSavingsContributionStatus.CONFIRMED);

        SavingsCycleStatus updatedStatus = cycle.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        if (cycle.getStatus() != SavingsCycleStatus.CANCELLED) {
            if (allConfirmed) {
                updatedStatus = SavingsCycleStatus.COMPLETED;
            } else if (now.isBefore(cycle.getWindowStart())) {
                updatedStatus = SavingsCycleStatus.PLANNED;
            } else if (now.isAfter(cycle.getWindowEnd())) {
                boolean anyProgress = contributions.stream()
                        .anyMatch(contribution -> contribution.getStatus() == MemberSavingsContributionStatus.CONFIRMED
                                || contribution.getStatus() == MemberSavingsContributionStatus.PARTIALLY_CONFIRMED);
                updatedStatus = anyProgress ? SavingsCycleStatus.RECONCILING : SavingsCycleStatus.MISSED;
            } else {
                updatedStatus = SavingsCycleStatus.OPEN;
            }
        }
        if (updatedStatus != cycle.getStatus()) {
            cycle.setStatus(updatedStatus);
            cycle.setClosedAt(updatedStatus == SavingsCycleStatus.COMPLETED ? now : cycle.getClosedAt());
            return savingsCycleRepository.save(cycle);
        }
        if (updatedStatus == SavingsCycleStatus.COMPLETED && cycle.getClosedAt() == null) {
            cycle.setClosedAt(now);
            return savingsCycleRepository.save(cycle);
        }
        return cycle;
    }

    private SavingsCycleResponse toResponse(SavingsCycle cycle) {
        List<MemberSavingsContribution> contributions = memberSavingsContributionRepository.findByCycleIdOrderByCreatedAtAsc(cycle.getId());
        int confirmedMembersCount = (int) contributions.stream()
                .filter(contribution -> contribution.getStatus() == MemberSavingsContributionStatus.CONFIRMED)
                .count();
        return new SavingsCycleResponse(
                cycle.getId(),
                cycle.getCircleId(),
                cycle.getCycleNumber(),
                cycle.getCountryCode(),
                cycle.getCurrency(),
                cycle.getWindowStart(),
                cycle.getWindowEnd(),
                cycle.getExpectedAmountPerMember(),
                cycle.getParticipantCount(),
                confirmedMembersCount,
                cycle.getStatus(),
                cycle.getNote(),
                cycle.getClosedAt(),
                cycle.getCreatedAt(),
                cycle.getUpdatedAt()
        );
    }

    private SavingsCycleStatus resolveInitialStatus(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(windowStart)) {
            return SavingsCycleStatus.PLANNED;
        }
        if (now.isAfter(windowEnd)) {
            return SavingsCycleStatus.RECONCILING;
        }
        return SavingsCycleStatus.OPEN;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
