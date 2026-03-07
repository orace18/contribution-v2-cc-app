package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.entity.CircleScore;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.dto.CircleProgressResponse;
import com.ccapp.circlesocial.dto.CircleScoreResponse;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circlesocial.repository.CircleScoreRepository;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleScoreService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CircleRepository circleRepository;
    private final CircleActivityRepository circleActivityRepository;
    private final CircleScoreRepository circleScoreRepository;
    private final CircleProgressService circleProgressService;

    @Transactional
    public CircleScoreResponse recalculateScore(UUID circleId) {
        ensureCircleExists(circleId);

        long activitiesCount = circleActivityRepository.countByCircleId(circleId);
        BigDecimal activityScore = BigDecimal.valueOf(Math.min(activitiesCount * 10L, 100L)).setScale(2, RoundingMode.HALF_UP);

        CircleProgressResponse progress = circleProgressService.recalculateCircleProgress(circleId);
        BigDecimal trainingScore = progress.membersCount() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(progress.membersCompletedTraining() * 100L)
                .divide(BigDecimal.valueOf(progress.membersCount()), 2, RoundingMode.HALF_UP);

        long loansGranted = circleActivityRepository.countByCircleIdAndActivityType(circleId, ActivityType.LOAN_GRANTED);
        long loansRepaid = circleActivityRepository.countByCircleIdAndActivityType(circleId, ActivityType.LOAN_REPAID);
        BigDecimal repaymentScore = loansGranted == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(loansRepaid * 100L)
                .divide(BigDecimal.valueOf(loansGranted), 2, RoundingMode.HALF_UP)
                .min(HUNDRED);

        BigDecimal globalScore = activityScore.multiply(BigDecimal.valueOf(0.30))
                .add(trainingScore.multiply(BigDecimal.valueOf(0.40)))
                .add(repaymentScore.multiply(BigDecimal.valueOf(0.30)))
                .setScale(2, RoundingMode.HALF_UP);

        CircleScore score = circleScoreRepository.findById(circleId)
                .orElseGet(() -> CircleScore.builder().circleId(circleId).build());
        score.setActivityScore(activityScore);
        score.setTrainingScore(trainingScore);
        score.setRepaymentScore(repaymentScore);
        score.setGlobalScore(globalScore);

        CircleScore saved = circleScoreRepository.save(score);
        return toResponse(saved);
    }

    @Transactional
    public CircleScoreResponse getScore(UUID circleId) {
        ensureCircleExists(circleId);
        return circleScoreRepository.findById(circleId)
                .map(this::toResponse)
                .orElseGet(() -> recalculateScore(circleId));
    }

    private void ensureCircleExists(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
    }

    private CircleScoreResponse toResponse(CircleScore score) {
        return new CircleScoreResponse(
                score.getCircleId(),
                score.getActivityScore(),
                score.getTrainingScore(),
                score.getRepaymentScore(),
                score.getGlobalScore(),
                score.getUpdatedAt()
        );
    }
}
