package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.entity.CircleProgress;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.dto.CircleProgressResponse;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circlesocial.repository.CircleProgressRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleProgressService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleActivityRepository circleActivityRepository;
    private final CircleProgressRepository circleProgressRepository;

    @Transactional
    public CircleProgressResponse recalculateCircleProgress(UUID circleId) {
        ensureCircleExists(circleId);

        int membersCount = Math.toIntExact(circleMemberRepository.countByCircle_Id(circleId));
        int membersCompletedTraining = capByMembers(
                membersCount,
                circleActivityRepository.countDistinctActorsByCircleIdAndActivityType(circleId, ActivityType.TRAINING_COMPLETED)
        );
        int membersSubmittedProject = capByMembers(
                membersCount,
                circleActivityRepository.countDistinctActorsByCircleIdAndActivityType(circleId, ActivityType.PROJECT_SUBMITTED)
        );
        int membersPaidContribution = capByMembers(
                membersCount,
                circleActivityRepository.countDistinctActorsByCircleIdAndActivityType(circleId, ActivityType.CONTRIBUTION_PAID)
        );

        int totalDone = membersCompletedTraining + membersSubmittedProject + membersPaidContribution;
        int totalPossible = membersCount * 3;
        BigDecimal progressPercentage = totalPossible == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalDone * 100L)
                .divide(BigDecimal.valueOf(totalPossible), 2, RoundingMode.HALF_UP);

        CircleProgress progress = circleProgressRepository.findById(circleId)
                .orElseGet(() -> CircleProgress.builder().circleId(circleId).build());

        progress.setMembersCount(membersCount);
        progress.setMembersCompletedTraining(membersCompletedTraining);
        progress.setMembersSubmittedProject(membersSubmittedProject);
        progress.setMembersPaidContribution(membersPaidContribution);
        progress.setProgressPercentage(progressPercentage);

        CircleProgress saved = circleProgressRepository.save(progress);
        return toResponse(saved);
    }

    @Transactional
    public CircleProgressResponse getCircleProgress(UUID circleId) {
        ensureCircleExists(circleId);
        return circleProgressRepository.findById(circleId)
                .map(this::toResponse)
                .orElseGet(() -> recalculateCircleProgress(circleId));
    }

    private int capByMembers(int membersCount, long value) {
        return Math.min(membersCount, Math.toIntExact(value));
    }

    private void ensureCircleExists(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
    }

    private CircleProgressResponse toResponse(CircleProgress progress) {
        return new CircleProgressResponse(
                progress.getCircleId(),
                progress.getMembersCount(),
                progress.getMembersCompletedTraining(),
                progress.getMembersSubmittedProject(),
                progress.getMembersPaidContribution(),
                progress.getProgressPercentage(),
                progress.getUpdatedAt()
        );
    }
}
