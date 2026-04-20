package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.seedcapital.readiness.domain.entity.CircleCoachAssignment;
import com.ccapp.seedcapital.readiness.domain.enums.CircleCoachAssignmentSource;
import com.ccapp.seedcapital.readiness.dto.AssignCircleCoachRequest;
import com.ccapp.seedcapital.readiness.dto.CircleCoachAssignmentResponse;
import com.ccapp.seedcapital.readiness.dto.CircleNetworkCoachingResponse;
import com.ccapp.seedcapital.readiness.repository.CircleCoachAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleCoachAssignmentService {

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CircleCoachAssignmentRepository circleCoachAssignmentRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final CircleNetworkCoachingService circleNetworkCoachingService;

    @Transactional
    public CircleCoachAssignmentResponse assignCoach(UUID requesterAccountHolderId,
                                                     UUID circleId,
                                                     AssignCircleCoachRequest request) {
        circleReadinessAccessService.assertLegacyOwner(requesterAccountHolderId, circleId);
        return upsertManualAssignment(
                requesterAccountHolderId,
                circleId,
                request.coachAccountHolderId(),
                request.note()
        );
    }

    @Transactional
    public CircleCoachAssignmentResponse syncManualAssignmentFromMemberRoleChange(UUID actorAccountHolderId,
                                                                                  UUID circleId,
                                                                                  UUID coachAccountHolderId,
                                                                                  String note) {
        return upsertManualAssignment(actorAccountHolderId, circleId, coachAccountHolderId, note);
    }

    @Transactional(readOnly = true)
    public CircleCoachAssignmentResponse getAssignment(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        return circleCoachAssignmentRepository.findById(circleId)
                .map(this::toResponse)
                .orElseGet(() -> inheritedAssignmentResponse(circleId));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findAssignedCoachAccountHolderId(UUID circleId) {
        Optional<UUID> explicitCoach = circleCoachAssignmentRepository.findById(circleId)
                .map(CircleCoachAssignment::getCoachAccountHolderId);
        if (explicitCoach.isPresent()) {
            return explicitCoach;
        }
        CircleNetworkCoachingService.NetworkCoachingSnapshot snapshot = circleNetworkCoachingService.resolveSnapshot(circleId);
        return snapshot.coachResolved() ? Optional.of(snapshot.coachAccountHolderId()) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean hasAssignedCoach(UUID circleId) {
        return circleCoachAssignmentRepository.existsById(circleId)
                || circleNetworkCoachingService.resolveSnapshot(circleId).coachResolved();
    }

    @Transactional(readOnly = true)
    public CircleNetworkCoachingResponse getNetworkCoaching(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        CircleNetworkCoachingService.NetworkCoachingSnapshot snapshot = circleNetworkCoachingService.resolveSnapshot(circleId);
        return new CircleNetworkCoachingResponse(
                snapshot.circleId(),
                snapshot.coachResolved(),
                snapshot.coachAccountHolderId(),
                snapshot.membersCount(),
                snapshot.lineageCoverageCount(),
                snapshot.lineageCoverageRequired(),
                snapshot.lineageCoverageScore(),
                snapshot.coachLevel(),
                snapshot.advisorAccountHolderIds(),
                snapshot.coachGrade(),
                snapshot.coachTeamSize(),
                snapshot.networkSignalScore(),
                snapshot.coachLinkedAt()
        );
    }

    private CircleCoachAssignmentResponse toResponse(CircleCoachAssignment assignment) {
        return new CircleCoachAssignmentResponse(
                assignment.getCircleId(),
                true,
                assignment.getCoachAccountHolderId(),
                assignment.getSource(),
                assignment.getNote(),
                assignment.getAssignedByAccountHolderId(),
                assignment.getAssignedAt(),
                assignment.getUpdatedAt()
        );
    }

    private CircleCoachAssignmentResponse inheritedAssignmentResponse(UUID circleId) {
        CircleNetworkCoachingService.NetworkCoachingSnapshot snapshot = circleNetworkCoachingService.resolveSnapshot(circleId);
        if (!snapshot.coachResolved()) {
            return new CircleCoachAssignmentResponse(
                    circleId,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return new CircleCoachAssignmentResponse(
                circleId,
                true,
                snapshot.coachAccountHolderId(),
                CircleCoachAssignmentSource.NETWORK_INHERITED,
                "Inherited from MLM network lineage",
                null,
                snapshot.coachLinkedAt(),
                null
        );
    }

    private CircleCoachAssignmentResponse upsertManualAssignment(UUID actorAccountHolderId,
                                                                 UUID circleId,
                                                                 UUID coachAccountHolderId,
                                                                 String note) {
        circleReadinessAccessService.requireActiveCircle(circleId);
        accountHolderRepository.findById(coachAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Coach account holder not found"));

        CircleCoachAssignment assignment = circleCoachAssignmentRepository.findById(circleId)
                .orElseGet(() -> CircleCoachAssignment.builder().circleId(circleId).build());
        assignment.setCoachAccountHolderId(coachAccountHolderId);
        assignment.setAssignedByAccountHolderId(actorAccountHolderId);
        assignment.setAssignedAt(OffsetDateTime.now());
        assignment.setSource(CircleCoachAssignmentSource.MANUAL);
        assignment.setNote(normalizeOptionalText(note));

        CircleCoachAssignment saved = circleCoachAssignmentRepository.save(assignment);
        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                circleId,
                actorAccountHolderId,
                ActivityType.COACH_ASSIGNED,
                coachAccountHolderId,
                "Coach assigned to circle"
        ));
        return toResponse(saved);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
