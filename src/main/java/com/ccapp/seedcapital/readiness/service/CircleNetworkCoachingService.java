package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.mlm.domain.entity.MlmMemberGradeProfile;
import com.ccapp.mlm.domain.entity.MlmMemberMetrics;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.enums.MlmMemberGrade;
import com.ccapp.mlm.repository.MlmMemberGradeRepository;
import com.ccapp.mlm.repository.MlmMemberMetricsRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleNetworkCoachingService {

    private static final int MAX_NETWORK_DEPTH = 4;
    private static final int MAX_ADVISORS = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal COVERAGE_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal PROXIMITY_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal ADVISORS_WEIGHT = new BigDecimal("0.15");
    private static final BigDecimal ENGAGEMENT_WEIGHT = new BigDecimal("0.15");

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmMemberGradeRepository mlmMemberGradeRepository;
    private final MlmMemberMetricsRepository mlmMemberMetricsRepository;

    @Transactional(readOnly = true)
    public NetworkCoachingSnapshot resolveSnapshot(UUID circleId) {
        circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));
        List<CircleMember> members = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId);
        if (members.isEmpty()) {
            return NetworkCoachingSnapshot.empty(circleId);
        }

        int membersCount = members.size();
        int lineageCoverageRequired = minimumCoverageRequired(membersCount);
        Set<UUID> circleMemberIds = members.stream()
                .map(CircleMember::getAccountHolderId)
                .collect(HashSet::new, Set::add, Set::addAll);

        Map<UUID, CandidateAccumulator> candidates = new HashMap<>();
        for (CircleMember member : members) {
            Set<UUID> seenAncestorsForMember = new HashSet<>();
            for (MlmRelationship relation : mlmRelationshipRepository.findAncestors(member.getAccountHolderId())) {
                if (relation.getLevel() == null
                        || relation.getLevel() <= 0
                        || relation.getLevel() > MAX_NETWORK_DEPTH
                        || relation.getAncestorId() == null
                        || circleMemberIds.contains(relation.getAncestorId())
                        || !seenAncestorsForMember.add(relation.getAncestorId())) {
                    continue;
                }

                candidates.computeIfAbsent(relation.getAncestorId(), ignored -> new CandidateAccumulator())
                        .record(member.getAccountHolderId(), relation.getLevel(), relation.getCreatedAt());
            }
        }

        List<CandidateRank> rankedCandidates = candidates.entrySet().stream()
                .map(entry -> CandidateRank.from(entry.getKey(), entry.getValue()))
                .filter(candidate -> candidate.coverageCount() >= lineageCoverageRequired)
                .sorted(Comparator
                        .comparingInt(CandidateRank::coverageCount).reversed()
                        .thenComparingInt(candidate -> candidate.maxLevel() == null ? Integer.MAX_VALUE : candidate.maxLevel())
                        .thenComparingInt(CandidateRank::totalLevel)
                        .thenComparing(CandidateRank::firstLinkedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CandidateRank::accountHolderId))
                .toList();

        if (rankedCandidates.isEmpty()) {
            return NetworkCoachingSnapshot.empty(circleId, membersCount, lineageCoverageRequired);
        }

        CandidateRank coachCandidate = rankedCandidates.getFirst();
        List<UUID> advisorAccountHolderIds = rankedCandidates.stream()
                .skip(1)
                .limit(MAX_ADVISORS)
                .map(CandidateRank::accountHolderId)
                .toList();

        BigDecimal lineageCoverageScore = percentage(coachCandidate.coverageCount(), membersCount);
        int coachLevel = coachCandidate.maxLevel() == null ? MAX_NETWORK_DEPTH : coachCandidate.maxLevel();
        BigDecimal networkSignalScore = computeNetworkSignalScore(
                lineageCoverageScore,
                proximityScore(coachLevel),
                advisorsScore(advisorAccountHolderIds.size()),
                engagementScore(coachCandidate.accountHolderId())
        );

        String coachGrade = mlmMemberGradeRepository.findById(coachCandidate.accountHolderId())
                .map(MlmMemberGradeProfile::getGrade)
                .map(Enum::name)
                .orElse(null);
        long coachTeamSize = mlmMemberMetricsRepository.findById(coachCandidate.accountHolderId())
                .map(MlmMemberMetrics::getTeamSize)
                .orElseGet(() -> mlmRelationshipRepository.countTeamSize(coachCandidate.accountHolderId()));

        return new NetworkCoachingSnapshot(
                circleId,
                coachCandidate.accountHolderId(),
                membersCount,
                coachCandidate.coverageCount(),
                lineageCoverageRequired,
                lineageCoverageScore,
                coachLevel,
                advisorAccountHolderIds,
                coachGrade,
                coachTeamSize,
                networkSignalScore,
                coachCandidate.firstLinkedAt()
        );
    }

    private int minimumCoverageRequired(int membersCount) {
        if (membersCount <= 1) {
            return membersCount;
        }
        return Math.max(2, (int) Math.ceil(membersCount * (2.0 / 3.0)));
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal proximityScore(int coachLevel) {
        return switch (coachLevel) {
            case 1 -> HUNDRED;
            case 2 -> new BigDecimal("80.00");
            case 3 -> new BigDecimal("60.00");
            case 4 -> new BigDecimal("40.00");
            default -> new BigDecimal("20.00");
        };
    }

    private BigDecimal advisorsScore(int advisorsCount) {
        return BigDecimal.valueOf(Math.min(advisorsCount * 50L, 100L))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal engagementScore(UUID coachAccountHolderId) {
        BigDecimal gradeScore = mlmMemberGradeRepository.findById(coachAccountHolderId)
                .map(MlmMemberGradeProfile::getGrade)
                .map(this::gradeScore)
                .orElse(new BigDecimal("40.00"));
        BigDecimal teamSizeScore = mlmMemberMetricsRepository.findById(coachAccountHolderId)
                .map(MlmMemberMetrics::getTeamSize)
                .map(this::teamSizeScore)
                .orElseGet(() -> teamSizeScore(mlmRelationshipRepository.countTeamSize(coachAccountHolderId)));
        return gradeScore.max(teamSizeScore);
    }

    private BigDecimal gradeScore(MlmMemberGrade grade) {
        if (grade == null) {
            return new BigDecimal("40.00");
        }
        return switch (grade) {
            case CLASSIC -> new BigDecimal("40.00");
            case GOLD -> new BigDecimal("70.00");
            case DIAMOND -> HUNDRED;
        };
    }

    private BigDecimal teamSizeScore(long teamSize) {
        return BigDecimal.valueOf(Math.min(teamSize * 20L, 100L))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeNetworkSignalScore(BigDecimal coverageScore,
                                                 BigDecimal proximityScore,
                                                 BigDecimal advisorsScore,
                                                 BigDecimal engagementScore) {
        return coverageScore.multiply(COVERAGE_WEIGHT)
                .add(proximityScore.multiply(PROXIMITY_WEIGHT))
                .add(advisorsScore.multiply(ADVISORS_WEIGHT))
                .add(engagementScore.multiply(ENGAGEMENT_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static final class CandidateAccumulator {
        private final Set<UUID> coveredMembers = new HashSet<>();
        private int totalLevel = 0;
        private Integer maxLevel;
        private OffsetDateTime firstLinkedAt;

        private void record(UUID memberId, int level, OffsetDateTime linkedAt) {
            coveredMembers.add(memberId);
            totalLevel += level;
            maxLevel = maxLevel == null ? level : Math.max(maxLevel, level);
            if (firstLinkedAt == null || (linkedAt != null && linkedAt.isBefore(firstLinkedAt))) {
                firstLinkedAt = linkedAt;
            }
        }
    }

    private record CandidateRank(
            UUID accountHolderId,
            int coverageCount,
            int totalLevel,
            Integer maxLevel,
            OffsetDateTime firstLinkedAt
    ) {
        private static CandidateRank from(UUID accountHolderId, CandidateAccumulator accumulator) {
            return new CandidateRank(
                    accountHolderId,
                    accumulator.coveredMembers.size(),
                    accumulator.totalLevel,
                    accumulator.maxLevel,
                    accumulator.firstLinkedAt
            );
        }
    }

    public record NetworkCoachingSnapshot(
            UUID circleId,
            UUID coachAccountHolderId,
            int membersCount,
            int lineageCoverageCount,
            int lineageCoverageRequired,
            BigDecimal lineageCoverageScore,
            Integer coachLevel,
            List<UUID> advisorAccountHolderIds,
            String coachGrade,
            long coachTeamSize,
            BigDecimal networkSignalScore,
            OffsetDateTime coachLinkedAt
    ) {
        public static NetworkCoachingSnapshot empty(UUID circleId) {
            return empty(circleId, 0, 0);
        }

        public static NetworkCoachingSnapshot empty(UUID circleId, int membersCount, int lineageCoverageRequired) {
            return new NetworkCoachingSnapshot(
                    circleId,
                    null,
                    membersCount,
                    0,
                    lineageCoverageRequired,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null,
                    List.of(),
                    null,
                    0L,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null
            );
        }

        public boolean coachResolved() {
            return coachAccountHolderId != null;
        }

        public int advisorsCount() {
            return advisorAccountHolderIds == null ? 0 : advisorAccountHolderIds.size();
        }
    }
}
