package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.seedcapital.readiness.repository.CircleCoachAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleReadinessAccessService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleCoachAssignmentRepository circleCoachAssignmentRepository;
    private final CircleNetworkCoachingService circleNetworkCoachingService;

    @Transactional(readOnly = true)
    public Circle requireActiveCircle(UUID circleId) {
        return circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));
    }

    @Transactional(readOnly = true)
    public List<CircleMember> requireActiveMembers(UUID circleId) {
        requireActiveCircle(circleId);
        return circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId);
    }

    @Transactional(readOnly = true)
    public void assertCanViewReadiness(UUID requesterAccountHolderId, UUID circleId) {
        Circle circle = requireActiveCircle(circleId);
        if (circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, requesterAccountHolderId)
                || Objects.equals(circle.getCoachId(), requesterAccountHolderId)
                || isAssignedCoach(circleId, requesterAccountHolderId)) {
            return;
        }
        throw new BusinessException("Circle readiness access denied");
    }

    @Transactional(readOnly = true)
    public void assertLegacyOwner(UUID requesterAccountHolderId, UUID circleId) {
        Circle circle = requireActiveCircle(circleId);
        if (!Objects.equals(circle.getCoachId(), requesterAccountHolderId)) {
            throw new BusinessException("Only the circle owner can manage coach assignment");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanManageMeetings(UUID requesterAccountHolderId, UUID circleId) {
        assertCanManageReadiness(requesterAccountHolderId, circleId);
    }

    @Transactional(readOnly = true)
    public void assertCanManageReadiness(UUID requesterAccountHolderId, UUID circleId) {
        Circle circle = requireActiveCircle(circleId);
        if (Objects.equals(circle.getCoachId(), requesterAccountHolderId)
                || isAssignedCoach(circleId, requesterAccountHolderId)) {
            return;
        }
        throw new BusinessException("Only the circle owner or assigned coach can manage readiness");
    }

    @Transactional(readOnly = true)
    public boolean isAssignedCoach(UUID circleId, UUID accountHolderId) {
        boolean explicitCoach = circleCoachAssignmentRepository.findById(circleId)
                .map(assignment -> Objects.equals(assignment.getCoachAccountHolderId(), accountHolderId))
                .orElse(false);
        if (explicitCoach) {
            return true;
        }
        CircleNetworkCoachingService.NetworkCoachingSnapshot snapshot = circleNetworkCoachingService.resolveSnapshot(circleId);
        return snapshot.coachResolved()
                && Objects.equals(snapshot.coachAccountHolderId(), accountHolderId);
    }

    @Transactional(readOnly = true)
    public void assertCircleMember(UUID requesterAccountHolderId, UUID circleId) {
        requireActiveCircle(circleId);
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, requesterAccountHolderId)) {
            throw new BusinessException("Circle membership is required");
        }
    }
}
