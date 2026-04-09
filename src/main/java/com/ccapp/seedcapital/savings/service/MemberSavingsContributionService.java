package com.ccapp.seedcapital.savings.service;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionConfirmationSource;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import com.ccapp.seedcapital.savings.dto.ConfirmSavingsContributionRequest;
import com.ccapp.seedcapital.savings.dto.MemberSavingsContributionResponse;
import com.ccapp.seedcapital.savings.repository.MemberSavingsContributionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberSavingsContributionService {

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final SavingsCycleService savingsCycleService;
    private final ImfSavingsChannelService imfSavingsChannelService;
    private final MemberSavingsContributionRepository memberSavingsContributionRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public MemberSavingsContributionResponse confirmMyContribution(UUID requesterAccountHolderId,
                                                                  UUID circleId,
                                                                  UUID cycleId,
                                                                  ConfirmSavingsContributionRequest request) {
        circleReadinessAccessService.assertCircleMember(requesterAccountHolderId, circleId);
        SavingsCycle cycle = savingsCycleService.refreshCycleStatus(savingsCycleService.requireCycle(circleId, cycleId));
        if (cycle.getStatus() == SavingsCycleStatus.CANCELLED
                || cycle.getStatus() == SavingsCycleStatus.COMPLETED
                || cycle.getStatus() == SavingsCycleStatus.MISSED) {
            throw new BusinessException("Savings contribution cannot be confirmed for this cycle status");
        }

        ImfSavingsChannel readyChannel = imfSavingsChannelService.requireReadyChannel(requesterAccountHolderId, cycle.getCountryCode());
        MemberSavingsContribution contribution = memberSavingsContributionRepository.findByCycleIdAndAccountHolderId(cycleId, requesterAccountHolderId)
                .orElseThrow(() -> new BusinessException("Savings contribution expectation not found for circle member"));

        BigDecimal confirmedAmount = request.confirmedAmount().setScale(4);
        contribution.setConfirmedAmount(confirmedAmount);
        contribution.setStatus(resolveStatus(confirmedAmount, contribution.getExpectedAmount()));
        contribution.setConfirmedAt(OffsetDateTime.now());
        contribution.setPartnerReference(normalizeOptionalText(request.partnerReference()));
        contribution.setTransactionReference(firstNonBlank(
                normalizeOptionalText(request.transactionReference()),
                readyChannel.getExternalChannelReference()
        ));
        contribution.setConfirmationSource(SavingsContributionConfirmationSource.MEMBER_DECLARATION);
        contribution.setConfirmationNote(normalizeOptionalText(request.confirmationNote()));

        MemberSavingsContribution saved = memberSavingsContributionRepository.save(contribution);
        savingsCycleService.refreshCycleStatus(cycle);

        if (confirmedAmount.compareTo(BigDecimal.ZERO) > 0) {
            domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                    circleId,
                    requesterAccountHolderId,
                    ActivityType.CONTRIBUTION_PAID,
                    saved.getId(),
                    "Member IMF savings contribution confirmed"
            ));
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MemberSavingsContributionResponse> listCycleContributions(UUID requesterAccountHolderId,
                                                                         UUID circleId,
                                                                         UUID cycleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        savingsCycleService.requireCycle(circleId, cycleId);
        return memberSavingsContributionRepository.findByCycleIdOrderByCreatedAtAsc(cycleId).stream()
                .map(this::toResponse)
                .toList();
    }

    private MemberSavingsContributionStatus resolveStatus(BigDecimal confirmedAmount, BigDecimal expectedAmount) {
        return confirmedAmount.compareTo(expectedAmount) >= 0
                ? MemberSavingsContributionStatus.CONFIRMED
                : MemberSavingsContributionStatus.PARTIALLY_CONFIRMED;
    }

    private MemberSavingsContributionResponse toResponse(MemberSavingsContribution contribution) {
        return new MemberSavingsContributionResponse(
                contribution.getId(),
                contribution.getCycleId(),
                contribution.getAccountHolderId(),
                contribution.getExpectedAmount(),
                contribution.getConfirmedAmount(),
                contribution.getStatus(),
                contribution.getConfirmedAt(),
                contribution.getPartnerReference(),
                contribution.getTransactionReference(),
                contribution.getConfirmationSource(),
                contribution.getConfirmationNote(),
                contribution.getCreatedAt(),
                contribution.getUpdatedAt()
        );
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
