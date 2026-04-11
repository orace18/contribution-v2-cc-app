package com.ccapp.payments.subscription.service;

import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipReconciliationServiceTest {

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private MembershipService membershipService;
    @Mock
    private MobileOnboardingJourneyRepository journeyRepository;
    @Mock
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Mock
    private ObjectProvider<SubscriptionPaymentFinalizationService> subscriptionPaymentFinalizationServiceProvider;
    @Mock
    private ObjectProvider<MobileOnboardingJourneyService> mobileOnboardingJourneyServiceProvider;
    @Mock
    private SubscriptionPaymentFinalizationService subscriptionPaymentFinalizationService;
    @Mock
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;

    private MembershipReconciliationService membershipReconciliationService;

    @BeforeEach
    void setUp() {
        membershipReconciliationService = new MembershipReconciliationService(
                membershipRepository,
                operationRepository,
                membershipService,
                journeyRepository,
                mlmRelationshipRepository,
                subscriptionPaymentFinalizationServiceProvider,
                mobileOnboardingJourneyServiceProvider
        );
        ReflectionTestUtils.setField(membershipReconciliationService, "batchSize", 50);
    }

    @Test
    void reconcileMembership_shouldFinalizeConfirmedPendingMembership() {
        UUID membershipId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Membership pendingMembership = membership(membershipId, operationId, MembershipStatus.PENDING);
        Membership activeMembership = membership(membershipId, operationId, MembershipStatus.ACTIVE);
        Operation confirmedOperation = operation(operationId, OperationStatus.CONFIRMED);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(pendingMembership));
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(confirmedOperation));
        when(membershipRepository.findByOperationId(operationId)).thenReturn(Optional.of(activeMembership));
        when(subscriptionPaymentFinalizationServiceProvider.getObject()).thenReturn(subscriptionPaymentFinalizationService);

        MembershipReconciliationService.ReconciliationResult result =
                membershipReconciliationService.reconcileMembership(membershipId);

        verify(subscriptionPaymentFinalizationService).finalizeConfirmedOperation(operationId);
        assertEquals("FINALIZED", result.action());
        assertEquals("ACTIVE", result.currentStatus());
    }

    @Test
    void reconcileMembership_shouldMarkFailedPendingMembershipAndNotifyOnboarding() {
        UUID membershipId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Membership pendingMembership = membership(membershipId, operationId, MembershipStatus.PENDING);
        Membership failedMembership = membership(membershipId, operationId, MembershipStatus.FAILED);
        Operation failedOperation = operation(operationId, OperationStatus.FAILED);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(pendingMembership));
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(failedOperation));
        when(membershipService.failPendingMembership(operationId)).thenReturn(failedMembership);
        when(mobileOnboardingJourneyServiceProvider.getIfAvailable()).thenReturn(mobileOnboardingJourneyService);

        MembershipReconciliationService.ReconciliationResult result =
                membershipReconciliationService.reconcileMembership(membershipId);

        verify(membershipService).failPendingMembership(operationId);
        verify(mobileOnboardingJourneyService).onPaidPaymentFailed(failedOperation);
        assertEquals("MARKED_FAILED", result.action());
        assertEquals("FAILED", result.currentStatus());
    }

    @Test
    void reconcileOperation_shouldReplayConfirmedActiveMembershipWhenPlacementIsStillMissing() {
        UUID accountHolderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Membership activeMembership = membership(UUID.randomUUID(), operationId, MembershipStatus.ACTIVE);
        activeMembership.setAccountHolderId(accountHolderId);
        Operation confirmedOperation = operation(operationId, OperationStatus.CONFIRMED);
        confirmedOperation.setAccountHolderId(accountHolderId);
        MobileOnboardingJourney journey = MobileOnboardingJourney.builder()
                .accountHolderId(accountHolderId)
                .status(MobileOnboardingJourneyStatus.ACTIVE_MEMBER)
                .sponsorSet(true)
                .sponsorAccountHolderId(UUID.randomUUID())
                .networkLinked(false)
                .build();

        when(membershipRepository.findByOperationId(operationId)).thenReturn(Optional.of(activeMembership));
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(confirmedOperation));
        when(journeyRepository.findByAccountHolderId(accountHolderId)).thenReturn(Optional.of(journey));
        when(mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(accountHolderId, 0)).thenReturn(0L);
        when(membershipRepository.findById(activeMembership.getId())).thenReturn(Optional.of(activeMembership));
        when(subscriptionPaymentFinalizationServiceProvider.getObject()).thenReturn(subscriptionPaymentFinalizationService);

        membershipReconciliationService.reconcileOperation(operationId);

        verify(subscriptionPaymentFinalizationService).finalizeConfirmedOperation(operationId);
    }

    @Test
    void reconcilePendingMembershipBatch_shouldContinueAfterOneFailure() {
        UUID failedMembershipId = UUID.randomUUID();
        UUID failedOperationId = UUID.randomUUID();
        UUID confirmedMembershipId = UUID.randomUUID();
        UUID confirmedOperationId = UUID.randomUUID();
        Membership firstPending = membership(failedMembershipId, failedOperationId, MembershipStatus.PENDING);
        Membership secondPending = membership(confirmedMembershipId, confirmedOperationId, MembershipStatus.PENDING);
        Membership activeMembership = membership(confirmedMembershipId, confirmedOperationId, MembershipStatus.ACTIVE);
        Operation confirmedOperation = operation(confirmedOperationId, OperationStatus.CONFIRMED);

        when(membershipRepository.findByStatusOrderByCreatedAtAsc(eq(MembershipStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(firstPending, secondPending));
        when(operationRepository.findById(failedOperationId)).thenThrow(new IllegalStateException("boom"));
        when(operationRepository.findById(confirmedOperationId)).thenReturn(Optional.of(confirmedOperation));
        when(membershipRepository.findByOperationId(confirmedOperationId)).thenReturn(Optional.of(activeMembership));
        when(subscriptionPaymentFinalizationServiceProvider.getObject()).thenReturn(subscriptionPaymentFinalizationService);

        List<MembershipReconciliationService.ReconciliationResult> results =
                membershipReconciliationService.reconcilePendingMembershipBatch();

        verify(subscriptionPaymentFinalizationService).finalizeConfirmedOperation(confirmedOperationId);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(result -> "ERROR".equals(result.action())));
        assertTrue(results.stream().anyMatch(result -> "FINALIZED".equals(result.action())));
    }

    private Membership membership(UUID membershipId, UUID operationId, MembershipStatus status) {
        return Membership.builder()
                .id(membershipId)
                .operationId(operationId)
                .accountHolderId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .planCode("CLASSIC_12M")
                .countryCode("BJ")
                .currency("XOF")
                .amount(new BigDecimal("19000"))
                .status(status)
                .activatedAt(status == MembershipStatus.ACTIVE ? OffsetDateTime.now().minusDays(1) : null)
                .expiresAt(status == MembershipStatus.ACTIVE ? OffsetDateTime.now().plusDays(30) : null)
                .build();
    }

    private Operation operation(UUID operationId, OperationStatus status) {
        return Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(status)
                .amount(new BigDecimal("19000"))
                .currency("XOF")
                .build();
    }
}
