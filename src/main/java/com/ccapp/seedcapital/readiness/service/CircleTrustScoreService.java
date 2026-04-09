package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.seedcapital.readiness.domain.entity.CircleTrustScore;
import com.ccapp.seedcapital.readiness.dto.CircleTrustScoreResponse;
import com.ccapp.seedcapital.readiness.repository.CircleTrustScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleTrustScoreService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal MEMBERSHIP_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal SIZE_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal MEETINGS_WEIGHT = new BigDecimal("0.25");
    private static final BigDecimal ACTIVITY_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal COACH_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal MAX_NETWORK_BONUS_POINTS = new BigDecimal("8.00");
    private static final long TRUST_SCORE_WINDOW_DAYS = 60L;

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CircleMeetingService circleMeetingService;
    private final CircleCoachAssignmentService circleCoachAssignmentService;
    private final CircleNetworkCoachingService circleNetworkCoachingService;
    private final CircleTrustScoreRepository circleTrustScoreRepository;
    private final CircleActivityRepository circleActivityRepository;
    private final FinancialAccessPolicy financialAccessPolicy;

    @Transactional
    public CircleTrustScoreResponse recalculateScore(UUID circleId) {
        List<CircleMember> members = circleReadinessAccessService.requireActiveMembers(circleId);
        int membersCount = members.size();
        int financiallyReadyMembersCount = (int) members.stream()
                .filter(member -> financialAccessPolicy.isAllowed(member.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS))
                .count();
        int completedMeetingsCount = Math.toIntExact(circleMeetingService.countCompletedMeetingsWithQuorumSince(
                circleId,
                OffsetDateTime.now().minusDays(TRUST_SCORE_WINDOW_DAYS)
        ));
        int recentActivityCount = Math.toIntExact(circleActivityRepository.countByCircleIdAndCreatedAtAfter(
                circleId,
                OffsetDateTime.now().minusDays(TRUST_SCORE_WINDOW_DAYS)
        ));
        boolean coachAssigned = circleCoachAssignmentService.hasAssignedCoach(circleId);
        CircleNetworkCoachingService.NetworkCoachingSnapshot networkSnapshot = circleNetworkCoachingService.resolveSnapshot(circleId);

        BigDecimal membershipComplianceScore = membersCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(financiallyReadyMembersCount * 100L)
                .divide(BigDecimal.valueOf(membersCount), 2, RoundingMode.HALF_UP);
        BigDecimal circleSizeScore = isValidCircleSize(membersCount)
                ? HUNDRED
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal meetingsScore = BigDecimal.valueOf(Math.min(completedMeetingsCount * 50L, 100L))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal activityScore = BigDecimal.valueOf(Math.min(recentActivityCount * 20L, 100L))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal coachScore = coachAssigned
                ? HUNDRED
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal baseScore = membershipComplianceScore.multiply(MEMBERSHIP_WEIGHT)
                .add(circleSizeScore.multiply(SIZE_WEIGHT))
                .add(meetingsScore.multiply(MEETINGS_WEIGHT))
                .add(activityScore.multiply(ACTIVITY_WEIGHT))
                .add(coachScore.multiply(COACH_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal networkBonusScore = qualifiesForNetworkBonus(membershipComplianceScore, circleSizeScore, completedMeetingsCount)
                ? MAX_NETWORK_BONUS_POINTS.multiply(networkSnapshot.networkSignalScore())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal globalScore = baseScore.add(networkBonusScore).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);

        CircleTrustScore score = circleTrustScoreRepository.findById(circleId)
                .orElseGet(() -> CircleTrustScore.builder().circleId(circleId).build());
        score.setMembersCount(membersCount);
        score.setFinanciallyReadyMembersCount(financiallyReadyMembersCount);
        score.setCompletedMeetingsCount(completedMeetingsCount);
        score.setRecentActivityCount(recentActivityCount);
        score.setCoachAssigned(coachAssigned);
        score.setMembershipComplianceScore(membershipComplianceScore);
        score.setCircleSizeScore(circleSizeScore);
        score.setMeetingsScore(meetingsScore);
        score.setActivityScore(activityScore);
        score.setCoachScore(coachScore);
        score.setNetworkSignalScore(networkSnapshot.networkSignalScore());
        score.setNetworkBonusScore(networkBonusScore);
        score.setNetworkAdvisorsCount(networkSnapshot.advisorsCount());
        score.setNetworkCoachLevel(networkSnapshot.coachLevel());
        score.setGlobalScore(globalScore);

        CircleTrustScore saved = circleTrustScoreRepository.save(score);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CircleTrustScoreResponse getScore(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        return circleTrustScoreRepository.findById(circleId)
                .map(this::toResponse)
                .orElseGet(() -> recalculateScore(circleId));
    }

    @Transactional(readOnly = true)
    public CircleTrustScoreResponse getScoreForEvaluation(UUID circleId) {
        circleReadinessAccessService.requireActiveCircle(circleId);
        return circleTrustScoreRepository.findById(circleId)
                .map(this::toResponse)
                .orElseGet(() -> recalculateScore(circleId));
    }

    private CircleTrustScoreResponse toResponse(CircleTrustScore score) {
        return new CircleTrustScoreResponse(
                score.getCircleId(),
                score.getMembersCount(),
                score.getFinanciallyReadyMembersCount(),
                score.getCompletedMeetingsCount(),
                score.getRecentActivityCount(),
                score.isCoachAssigned(),
                score.getMembershipComplianceScore(),
                score.getCircleSizeScore(),
                score.getMeetingsScore(),
                score.getActivityScore(),
                score.getCoachScore(),
                score.getNetworkSignalScore(),
                score.getNetworkBonusScore(),
                score.getNetworkAdvisorsCount(),
                score.getNetworkCoachLevel(),
                score.getGlobalScore(),
                score.getUpdatedAt()
        );
    }

    private boolean isValidCircleSize(int membersCount) {
        return membersCount >= 3 && membersCount <= 6;
    }

    private boolean qualifiesForNetworkBonus(BigDecimal membershipComplianceScore,
                                             BigDecimal circleSizeScore,
                                             int completedMeetingsCount) {
        return membershipComplianceScore.compareTo(new BigDecimal("80.00")) >= 0
                && circleSizeScore.compareTo(HUNDRED) >= 0
                && completedMeetingsCount >= 2;
    }
}
