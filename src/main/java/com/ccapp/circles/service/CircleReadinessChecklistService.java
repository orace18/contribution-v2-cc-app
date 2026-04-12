package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.dto.CircleReadinessChecklistResponse;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.financial.access.service.MemberFinancialProfileService;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.seedcapital.readiness.service.CircleTrustScoreService;
import com.ccapp.seedcapital.savings.service.ImfSavingsGateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleReadinessChecklistService {

    private static final BigDecimal MIN_RELIABILITY_SCORE = new BigDecimal("70.00");
    private static final int TOTAL_CHECKLIST_ITEMS = 7;

    private final CircleMemberRepository circleMemberRepository;
    private final MemberFinancialProfileService memberFinancialProfileService;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final CircleTrustScoreService circleTrustScoreService;
    private final ImfSavingsGateService imfSavingsGateService;

    @Transactional
    public CircleReadinessChecklistResponse evaluate(UUID circleId) {
        List<CircleMember> members = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId);
        if (members.isEmpty()) {
            return new CircleReadinessChecklistResponse(circleId, false, false, false, false, false, false, false, 0, TOTAL_CHECKLIST_ITEMS);
        }

        boolean allMembersInNetwork = true;
        boolean allMembershipsActive = true;
        boolean allWalletsActive = true;
        boolean allKycCompleted = true;
        boolean allImfLinked = true;

        for (CircleMember member : members) {
            UUID memberId = member.getAccountHolderId();
            if (!mlmMemberLinkRepository.existsById(memberId)) {
                allMembersInNetwork = false;
            }
            MemberFinancialProfile profile = memberFinancialProfileService.getProfile(memberId);
            // membershipActive is required by SeedCapitalEligibilityService (SEED_CAPITAL_ACCESS).
            // Must appear in the checklist to avoid showing ready=true while seed capital remains blocked.
            if (!profile.membershipActive()) allMembershipsActive = false;
            if (!profile.walletActive()) allWalletsActive = false;
            if (profile.blockingReasons().contains(FinancialAccessReasonCode.KYC_NOT_VERIFIED)) allKycCompleted = false;
            if (!profile.imfChannelReady()) allImfLinked = false;
        }

        boolean contributionsStarted = imfSavingsGateService.evaluate(circleId).qualifyingSavingsCyclesCount() > 0;
        boolean reliabilityScoreReached = circleTrustScoreService
                .getScoreForEvaluation(circleId)
                .globalScore()
                .compareTo(MIN_RELIABILITY_SCORE) >= 0;

        int completedItems = countTrue(allMembersInNetwork, allMembershipsActive, allWalletsActive,
                allKycCompleted, allImfLinked, contributionsStarted, reliabilityScoreReached);
        boolean ready = completedItems == TOTAL_CHECKLIST_ITEMS;

        return new CircleReadinessChecklistResponse(
                circleId, ready,
                allMembersInNetwork, allMembershipsActive, allWalletsActive, allKycCompleted,
                allImfLinked, contributionsStarted, reliabilityScoreReached,
                completedItems, TOTAL_CHECKLIST_ITEMS
        );
    }

    private int countTrue(boolean... flags) {
        int count = 0;
        for (boolean flag : flags) {
            if (flag) count++;
        }
        return count;
    }
}
