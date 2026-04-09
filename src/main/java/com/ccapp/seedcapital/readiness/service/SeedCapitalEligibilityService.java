package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.seedcapital.readiness.domain.entity.SeedCapitalEligibility;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalSatisfiedCriterion;
import com.ccapp.seedcapital.readiness.dto.CircleTrustScoreResponse;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalEligibilityResponse;
import com.ccapp.seedcapital.readiness.repository.SeedCapitalEligibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeedCapitalEligibilityService {

    private static final int MIN_MEMBERS = 3;
    private static final int MAX_MEMBERS = 6;
    private static final int MIN_COMPLETED_MEETINGS = 2;
    private static final BigDecimal MIN_TRUST_SCORE = new BigDecimal("70.00");
    private static final long ELIGIBILITY_WINDOW_DAYS = 60L;

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CircleMeetingService circleMeetingService;
    private final CircleCoachAssignmentService circleCoachAssignmentService;
    private final CircleTrustScoreService circleTrustScoreService;
    private final SeedCapitalEligibilityRepository seedCapitalEligibilityRepository;
    private final FinancialAccessPolicy financialAccessPolicy;

    @Transactional
    public SeedCapitalEligibilityResponse evaluate(UUID circleId) {
        List<CircleMember> members = circleReadinessAccessService.requireActiveMembers(circleId);
        int membersCount = members.size();
        boolean validCircleSize = membersCount >= MIN_MEMBERS && membersCount <= MAX_MEMBERS;
        boolean membershipCompliant = membersCount > 0 && members.stream()
                .allMatch(member -> financialAccessPolicy.isAllowed(member.getAccountHolderId(), FinancialAccessUseCase.SEED_CAPITAL_ACCESS));
        boolean meetingsRequirementMet = circleMeetingService.countCompletedMeetingsWithQuorumSince(
                circleId,
                OffsetDateTime.now().minusDays(ELIGIBILITY_WINDOW_DAYS)
        ) >= MIN_COMPLETED_MEETINGS;
        boolean coachAssigned = circleCoachAssignmentService.hasAssignedCoach(circleId);
        CircleTrustScoreResponse trustScore = circleTrustScoreService.getScoreForEvaluation(circleId);
        boolean trustScoreThresholdMet = trustScore.globalScore().compareTo(MIN_TRUST_SCORE) >= 0;

        EnumSet<SeedCapitalSatisfiedCriterion> satisfiedCriteria = EnumSet.noneOf(SeedCapitalSatisfiedCriterion.class);
        EnumSet<SeedCapitalBlockingReason> blockingReasons = EnumSet.noneOf(SeedCapitalBlockingReason.class);

        if (validCircleSize) {
            satisfiedCriteria.add(SeedCapitalSatisfiedCriterion.VALID_CIRCLE_SIZE);
        } else {
            blockingReasons.add(SeedCapitalBlockingReason.INVALID_CIRCLE_SIZE);
        }
        if (membershipCompliant) {
            satisfiedCriteria.add(SeedCapitalSatisfiedCriterion.MEMBERSHIP_COMPLIANT);
        } else {
            blockingReasons.add(SeedCapitalBlockingReason.MEMBERSHIP_NON_COMPLIANT);
        }
        if (meetingsRequirementMet) {
            satisfiedCriteria.add(SeedCapitalSatisfiedCriterion.MEETINGS_REGULAR);
        } else {
            blockingReasons.add(SeedCapitalBlockingReason.MEETINGS_NOT_REGULAR);
        }
        if (coachAssigned) {
            satisfiedCriteria.add(SeedCapitalSatisfiedCriterion.COACH_ASSIGNED);
        } else {
            blockingReasons.add(SeedCapitalBlockingReason.COACH_NOT_ASSIGNED);
        }
        if (trustScoreThresholdMet) {
            satisfiedCriteria.add(SeedCapitalSatisfiedCriterion.TRUST_SCORE_THRESHOLD_MET);
        } else {
            blockingReasons.add(SeedCapitalBlockingReason.TRUST_SCORE_BELOW_THRESHOLD);
        }

        List<SeedCapitalPendingCriterion> pendingCriteria = List.of(
                SeedCapitalPendingCriterion.IMF_CHANNEL_ACTIVE,
                SeedCapitalPendingCriterion.SAVINGS_DISCIPLINE_PROVEN
        );
        boolean eligible = blockingReasons.isEmpty();
        SeedCapitalEligibilityState state = !eligible
                ? SeedCapitalEligibilityState.BLOCKED
                : pendingCriteria.isEmpty()
                ? SeedCapitalEligibilityState.ELIGIBLE
                : SeedCapitalEligibilityState.READY_FOR_FUTURE_GATES;

        SeedCapitalEligibility eligibility = seedCapitalEligibilityRepository.findById(circleId)
                .orElseGet(() -> SeedCapitalEligibility.builder().circleId(circleId).build());
        eligibility.setEligible(eligible);
        eligibility.setState(state);
        eligibility.setTrustScore(trustScore.globalScore());
        eligibility.setValidCircleSize(validCircleSize);
        eligibility.setMembershipCompliant(membershipCompliant);
        eligibility.setMeetingsRequirementMet(meetingsRequirementMet);
        eligibility.setCoachAssigned(coachAssigned);
        eligibility.setTrustScoreThresholdMet(trustScoreThresholdMet);
        eligibility.setBlockingReasons(toCsv(blockingReasons));
        eligibility.setSatisfiedCriteria(toCsv(satisfiedCriteria));
        eligibility.setPendingCriteria(toCsv(pendingCriteria));
        eligibility.setEvaluatedAt(OffsetDateTime.now());

        SeedCapitalEligibility saved = seedCapitalEligibilityRepository.save(eligibility);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SeedCapitalEligibilityResponse getEligibility(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        return seedCapitalEligibilityRepository.findById(circleId)
                .map(this::toResponse)
                .orElseGet(() -> evaluate(circleId));
    }

    private SeedCapitalEligibilityResponse toResponse(SeedCapitalEligibility eligibility) {
        return new SeedCapitalEligibilityResponse(
                eligibility.getCircleId(),
                eligibility.isEligible(),
                eligibility.getState(),
                eligibility.getTrustScore(),
                eligibility.isValidCircleSize(),
                eligibility.isMembershipCompliant(),
                eligibility.isMeetingsRequirementMet(),
                eligibility.isCoachAssigned(),
                eligibility.isTrustScoreThresholdMet(),
                parseCsv(eligibility.getSatisfiedCriteria(), SeedCapitalSatisfiedCriterion.class),
                parseCsv(eligibility.getBlockingReasons(), SeedCapitalBlockingReason.class),
                parseCsv(eligibility.getPendingCriteria(), SeedCapitalPendingCriterion.class),
                eligibility.getEvaluatedAt()
        );
    }

    private String toCsv(Iterable<? extends Enum<?>> values) {
        StringBuilder builder = new StringBuilder();
        for (Enum<?> value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.name());
        }
        return builder.toString();
    }

    private <E extends Enum<E>> List<E> parseCsv(String csv, Class<E> enumType) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> Enum.valueOf(enumType, value))
                .toList();
    }
}
