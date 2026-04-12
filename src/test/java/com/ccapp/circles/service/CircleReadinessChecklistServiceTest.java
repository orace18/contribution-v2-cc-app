package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.CircleReadinessChecklistResponse;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.financial.access.service.MemberFinancialProfileService;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.seedcapital.readiness.dto.CircleTrustScoreResponse;
import com.ccapp.seedcapital.readiness.service.CircleTrustScoreService;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import com.ccapp.seedcapital.savings.dto.CircleSavingsGateResponse;
import com.ccapp.seedcapital.savings.service.ImfSavingsGateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircleReadinessChecklistServiceTest {

    @Mock CircleMemberRepository circleMemberRepository;
    @Mock MemberFinancialProfileService memberFinancialProfileService;
    @Mock MlmMemberLinkRepository mlmMemberLinkRepository;
    @Mock CircleTrustScoreService circleTrustScoreService;
    @Mock ImfSavingsGateService imfSavingsGateService;

    @InjectMocks CircleReadinessChecklistService service;

    private static final UUID CIRCLE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();

    // ── helpers ─────────────────────────────────────────────────────────────

    private CircleMember member(UUID accountHolderId) {
        return CircleMember.builder()
                .id(UUID.randomUUID())
                .circle(Circle.builder().id(CIRCLE_ID).status(CircleStatus.ACTIVE).build())
                .accountHolderId(accountHolderId)
                .build();
    }

    private MemberFinancialProfile readyProfile(UUID accountHolderId) {
        return new MemberFinancialProfile(
                accountHolderId,
                AccountStatus.ACTIVE, null,
                MembershipStatus.ACTIVE, null, true,  // membershipActive = true
                "ACTIVE", true,                        // walletActive = true
                false,
                FinancialAccessState.FINANCIAL_ALLOWED,
                true, "ACTIVE",
                List.of()
        );
    }

    private MemberFinancialProfile profileWithReasons(UUID accountHolderId,
                                                       List<FinancialAccessReasonCode> reasons) {
        return new MemberFinancialProfile(
                accountHolderId,
                AccountStatus.ACTIVE, null,
                MembershipStatus.ACTIVE, null, false,  // membershipActive = false
                "INACTIVE", false,                      // walletActive = false
                false,
                FinancialAccessState.FINANCIAL_DENIED,
                false, "MISSING",
                reasons
        );
    }

    private CircleSavingsGateResponse savingsGate(int qualifyingCycles) {
        return new CircleSavingsGateResponse(
                CIRCLE_ID, "BJ",
                qualifyingCycles > 0 ? ImfSavingsGateState.READY : ImfSavingsGateState.DISCIPLINE_INSUFFICIENT,
                1, 1, 1,
                qualifyingCycles, 3,
                OffsetDateTime.now()
        );
    }

    private CircleTrustScoreResponse trustScore(BigDecimal globalScore) {
        return new CircleTrustScoreResponse(
                CIRCLE_ID, 1, 1, 2, 1, true,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                0, null, globalScore, OffsetDateTime.now()
        );
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void evaluate_withAllConditionsMet_shouldReturnReady() {
        when(circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(CIRCLE_ID))
                .thenReturn(List.of(member(MEMBER_ID)));
        when(mlmMemberLinkRepository.existsById(MEMBER_ID)).thenReturn(true);
        when(memberFinancialProfileService.getProfile(MEMBER_ID)).thenReturn(readyProfile(MEMBER_ID));
        when(imfSavingsGateService.evaluate(CIRCLE_ID)).thenReturn(savingsGate(5));
        when(circleTrustScoreService.getScoreForEvaluation(CIRCLE_ID)).thenReturn(trustScore(new BigDecimal("75.00")));

        CircleReadinessChecklistResponse result = service.evaluate(CIRCLE_ID);

        assertTrue(result.ready());
        assertTrue(result.allMembersInNetwork());
        assertTrue(result.allMembershipsActive());
        assertTrue(result.allWalletsActive());
        assertTrue(result.allKycCompleted());
        assertTrue(result.allImfLinked());
        assertTrue(result.contributionsStarted());
        assertTrue(result.reliabilityScoreReached());
        assertEquals(7, result.completedItems());
        assertEquals(7, result.totalItems());
    }

    @Test
    void evaluate_withMissingKyc_shouldReflectInChecklist() {
        // User without KYC can be a circle member — but checklist shows it as incomplete
        when(circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(CIRCLE_ID))
                .thenReturn(List.of(member(MEMBER_ID)));
        when(mlmMemberLinkRepository.existsById(MEMBER_ID)).thenReturn(true);
        when(memberFinancialProfileService.getProfile(MEMBER_ID))
                .thenReturn(profileWithReasons(MEMBER_ID, List.of(FinancialAccessReasonCode.KYC_NOT_VERIFIED)));
        when(imfSavingsGateService.evaluate(CIRCLE_ID)).thenReturn(savingsGate(0));
        when(circleTrustScoreService.getScoreForEvaluation(CIRCLE_ID)).thenReturn(trustScore(new BigDecimal("50.00")));

        CircleReadinessChecklistResponse result = service.evaluate(CIRCLE_ID);

        assertFalse(result.ready());
        assertFalse(result.allKycCompleted());
        assertFalse(result.allMembershipsActive());
        assertFalse(result.allWalletsActive());
        assertFalse(result.allImfLinked());
        assertFalse(result.contributionsStarted());
        assertFalse(result.reliabilityScoreReached());
        // Only network is satisfied
        assertTrue(result.allMembersInNetwork());
        assertEquals(1, result.completedItems());
    }

    @Test
    void evaluate_withMemberNotInNetwork_shouldReflectInChecklist() {
        when(circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(CIRCLE_ID))
                .thenReturn(List.of(member(MEMBER_ID)));
        when(mlmMemberLinkRepository.existsById(MEMBER_ID)).thenReturn(false);
        when(memberFinancialProfileService.getProfile(MEMBER_ID)).thenReturn(readyProfile(MEMBER_ID));
        when(imfSavingsGateService.evaluate(CIRCLE_ID)).thenReturn(savingsGate(5));
        when(circleTrustScoreService.getScoreForEvaluation(CIRCLE_ID)).thenReturn(trustScore(new BigDecimal("75.00")));

        CircleReadinessChecklistResponse result = service.evaluate(CIRCLE_ID);

        assertFalse(result.ready());
        assertFalse(result.allMembersInNetwork());
        // All other conditions still met
        assertTrue(result.allMembershipsActive());
        assertTrue(result.allWalletsActive());
        assertTrue(result.allKycCompleted());
        assertTrue(result.allImfLinked());
        assertTrue(result.contributionsStarted());
        assertTrue(result.reliabilityScoreReached());
        // Seed capital implicitly blocked: ready=false → circle not eligible
        assertEquals(6, result.completedItems());
    }

    @Test
    void evaluate_notReady_seedCapitalShouldBeBlocked() {
        // When ready=false, the circle cannot qualify for seed capital.
        // SeedCapitalEligibilityService independently enforces SEED_CAPITAL_ACCESS per member,
        // which requires membership + KYC + wallet. This test documents that
        // a circle with any incomplete checklist item is NOT ready for seed capital.
        when(circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(CIRCLE_ID))
                .thenReturn(List.of(member(MEMBER_ID)));
        when(mlmMemberLinkRepository.existsById(any())).thenReturn(false); // network not linked
        when(memberFinancialProfileService.getProfile(any()))
                .thenReturn(profileWithReasons(MEMBER_ID, List.of(FinancialAccessReasonCode.MEMBERSHIP_MISSING)));
        when(imfSavingsGateService.evaluate(CIRCLE_ID)).thenReturn(savingsGate(0));
        when(circleTrustScoreService.getScoreForEvaluation(CIRCLE_ID)).thenReturn(trustScore(new BigDecimal("20.00")));

        CircleReadinessChecklistResponse result = service.evaluate(CIRCLE_ID);

        assertFalse(result.ready(), "Circle with no network, no membership, no wallet, no contributions, low score must not be ready");
        assertEquals(0, result.completedItems());
    }
}
