package com.ccapp.mobile.onboarding.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.graph.MlmGraphSyncService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.service.MLMPlacementService;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.entity.MobilePlanSelection;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.domain.enums.MobilePlanSelectionStatus;
import com.ccapp.mobile.onboarding.dto.MobileOnboardingStatusResponse;
import com.ccapp.mobile.onboarding.dto.MobileSponsorSelectionResponse;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEvent;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEventType;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.onboarding.repository.MobilePlanSelectionRepository;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.service.PaymentService;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MobileOnboardingJourneyServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SPONSOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PLAN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID OPERATION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock
    private MobileOnboardingJourneyRepository journeyRepository;
    @Mock
    private MobilePlanSelectionRepository mobilePlanSelectionRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private OperationService operationService;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private MembershipService membershipService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private MLMPlacementService mlmPlacementService;
    @Mock
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Mock
    private MlmMemberLinkRepository mlmMemberLinkRepository;
    @Mock
    private MlmGraphSyncService mlmGraphSyncService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private CountryContextService countryContextService;

    @InjectMocks
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(journeyRepository.save(any(MobileOnboardingJourney.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.save(any(Operation.class)))
                .thenAnswer(invocation -> {
                    Operation op = invocation.getArgument(0);
                    if (op.getId() == null) {
                        op.setId(OPERATION_ID);
                    }
                    return op;
                });
        when(countryContextService.resolve(ACCOUNT_ID))
                .thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(subscriptionService.resolveCatalogCountryCode("BJ")).thenReturn("BJ");
        when(mlmGraphSyncService.isNetworkReady(ACCOUNT_ID)).thenReturn(true);
        when(mlmMemberLinkRepository.existsById(ACCOUNT_ID)).thenReturn(false);
        when(mlmMemberLinkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void freePlanFlow_shouldCompleteOnboardingAndEmitEvents() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PLAN_SELECTED, true, SPONSOR_ID, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        SubscriptionPlan freePlan = plan(SubscriptionGrade.FREE, BigDecimal.ZERO.setScale(4));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID)).thenReturn(Optional.of(freePlan));
        when(operationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(membershipService.createPendingMembership(eq(OPERATION_ID), eq(ACCOUNT_ID), eq(freePlan)))
                .thenReturn(Membership.builder().status(MembershipStatus.PENDING).build());
        when(membershipService.activateForConfirmedOperation(any(Operation.class)))
                .thenReturn(Membership.builder()
                        .status(MembershipStatus.ACTIVE)
                        .expiresAt(OffsetDateTime.now().plusDays(30))
                        .build());
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false, true);

        var response = mobileOnboardingJourneyService.activateFreePlan(ACTOR_ID, ACCOUNT_ID);

        assertEquals("ONBOARDING_COMPLETED", response.journeyStatus());
        assertTrue(response.active());
        verify(mlmPlacementService).placeAccountHolder(ACTOR_ID, ACCOUNT_ID, SPONSOR_ID);
        assertPublishedEvent(MobileOnboardingEventType.MEMBERSHIP_ACTIVATED);
        assertPublishedEvent(MobileOnboardingEventType.NETWORK_LINKED);
        assertPublishedEvent(MobileOnboardingEventType.ONBOARDING_COMPLETED);
    }

    @Test
    void paidPlanFlow_shouldInitiateThenCompleteAfterConfirmation() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PLAN_SELECTED, true, SPONSOR_ID, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        SubscriptionPlan paidPlan = plan(SubscriptionGrade.CLASSIC, new BigDecimal("15000.0000"));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID)).thenReturn(Optional.of(paidPlan));

        Operation initiated = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .idempotencyKey("idem-001")
                .planId(PLAN_ID)
                .build();
        when(operationService.createInitiatedOperation(
                eq(ACCOUNT_ID),
                eq(ACTOR_ID),
                eq(OperationType.SUBSCRIPTION),
                eq(new BigDecimal("15000.0000")),
                eq("XOF"),
                any(),
                any(),
                eq(PLAN_ID)
        )).thenReturn(initiated);
        when(paymentService.initiatePayment(eq(OPERATION_ID), any(), eq(new BigDecimal("15000.0000")), eq("XOF"), eq(ACCOUNT_ID), eq(ACTOR_ID), any()))
                .thenReturn(new JawupayInitiateResponse("PSP-REF-001", "https://checkout"));
        Operation pending = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-001")
                .planId(PLAN_ID)
                .build();
        when(operationService.markPendingPsp(OPERATION_ID, "PSP-REF-001")).thenReturn(pending);
        when(membershipService.createPendingMembership(OPERATION_ID, ACCOUNT_ID, paidPlan))
                .thenReturn(Membership.builder().status(MembershipStatus.PENDING).build());
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false, true);

        MobilePaymentInitiateResponse paymentResponse = mobileOnboardingJourneyService
                .initiatePaidPlanPayment(ACTOR_ID, ACCOUNT_ID, "idem-001");

        assertEquals("PENDING_PSP", paymentResponse.status());
        assertEquals("PSP-REF-001", paymentResponse.pspReference());

        Operation confirmed = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-001")
                .planId(PLAN_ID)
                .build();
        mobileOnboardingJourneyService.onPaidPaymentConfirmed(confirmed);

        assertEquals(MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED, journey.getStatus());
        assertPublishedEvent(MobileOnboardingEventType.PAYMENT_INITIATED);
        assertPublishedEvent(MobileOnboardingEventType.PAYMENT_CONFIRMED);
        assertPublishedEvent(MobileOnboardingEventType.ONBOARDING_COMPLETED);
    }

    @Test
    void setSponsor_shouldAllowNoSponsorEdgeCase() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.COUNTRY_SELECTED, false, null, null);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));

        MobileSponsorSelectionResponse response = mobileOnboardingJourneyService.setSponsor(ACTOR_ID, ACCOUNT_ID, null);

        assertTrue(response.sponsorSet());
        assertNull(response.sponsorAccountHolderId());
        assertEquals(MobileOnboardingJourneyStatus.SPONSOR_SET, journey.getStatus());
        assertPublishedEvent(MobileOnboardingEventType.SPONSOR_SELECTED);
    }

    @Test
    void setSponsor_shouldAcceptValidSponsor() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.COUNTRY_SELECTED, false, null, null);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        when(accountHolderService.getById(SPONSOR_ID)).thenReturn(AccountHolder.builder().id(SPONSOR_ID).build());

        MobileSponsorSelectionResponse response = mobileOnboardingJourneyService.setSponsor(ACTOR_ID, ACCOUNT_ID, SPONSOR_ID);

        assertTrue(response.sponsorSet());
        assertEquals(SPONSOR_ID, response.sponsorAccountHolderId());
        assertEquals(MobileOnboardingJourneyStatus.SPONSOR_SET, journey.getStatus());
        verify(accountHolderService).getById(SPONSOR_ID);
    }

    @Test
    void freePlanWithoutSponsor_shouldCreateSelfNetworkNode() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PLAN_SELECTED, true, null, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        SubscriptionPlan freePlan = plan(SubscriptionGrade.FREE, BigDecimal.ZERO.setScale(4));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID)).thenReturn(Optional.of(freePlan));
        when(operationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(membershipService.createPendingMembership(eq(OPERATION_ID), eq(ACCOUNT_ID), eq(freePlan)))
                .thenReturn(Membership.builder().status(MembershipStatus.PENDING).build());
        when(membershipService.activateForConfirmedOperation(any(Operation.class)))
                .thenReturn(Membership.builder()
                        .status(MembershipStatus.ACTIVE)
                        .expiresAt(OffsetDateTime.now().plusDays(30))
                        .build());
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false, true);
        when(mlmRelationshipRepository.existsByAncestorIdAndDescendantId(ACCOUNT_ID, ACCOUNT_ID)).thenReturn(false);

        mobileOnboardingJourneyService.activateFreePlan(ACTOR_ID, ACCOUNT_ID);

        verify(mlmPlacementService, never()).placeAccountHolder(any(), any(), any());
        verify(mlmRelationshipRepository).save(any(MlmRelationship.class));
        assertEquals(MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED, journey.getStatus());
    }

    @Test
    void interruptedFlow_shouldResumeWithStatusApi() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PAYMENT_PENDING, true, SPONSOR_ID, PLAN_ID);
        journey.setPaymentOperationId(OPERATION_ID);
        journey.setPaymentReference("PSP-REF-001");
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        when(accountHolderService.getById(ACCOUNT_ID)).thenReturn(AccountHolder.builder()
                .id(ACCOUNT_ID)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .build());
        when(mobilePlanSelectionRepository.findByAccountHolderId(ACCOUNT_ID))
                .thenReturn(Optional.of(MobilePlanSelection.builder()
                        .accountHolderId(ACCOUNT_ID)
                        .planId(PLAN_ID)
                        .planCode("CLASSIC_1M")
                        .planName("Classic")
                        .countryCode("BJ")
                        .currency("XOF")
                        .status(MobilePlanSelectionStatus.PENDING_PAYMENT)
                        .build()));
        when(operationRepository.findById(OPERATION_ID)).thenReturn(Optional.of(Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .build()));
        when(membershipService.findLatestMembership(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false);

        MobileOnboardingStatusResponse response = mobileOnboardingJourneyService.getStatus(ACCOUNT_ID);

        assertEquals("PAYMENT_PENDING", response.currentStatus());
        assertFalse(response.isActive());
        assertFalse(response.isNetworkReady());
        assertNotNull(response.nextStep());
        assertEquals("WAIT_PAYMENT_CONFIRMATION", response.nextStep().code());
        assertTrue(response.completedSteps().contains("PLAN_SELECTED"));
    }

    private MobileOnboardingJourney journey(MobileOnboardingJourneyStatus status,
                                            boolean sponsorSet,
                                            UUID sponsorId,
                                            UUID planId) {
        return MobileOnboardingJourney.builder()
                .accountHolderId(ACCOUNT_ID)
                .status(status)
                .sponsorSet(sponsorSet)
                .sponsorAccountHolderId(sponsorId)
                .planId(planId)
                .planCode(planId == null ? null : "PLAN")
                .networkLinked(false)
                .onboardingCompleted(false)
                .build();
    }

    private SubscriptionPlan plan(SubscriptionGrade grade, BigDecimal amount) {
        return SubscriptionPlan.builder()
                .id(PLAN_ID)
                .code(grade.name() + "_1M")
                .name(grade.name())
                .grade(grade)
                .durationMonths(1)
                .country("BJ")
                .currency("XOF")
                .price(amount)
                .active(true)
                .build();
    }

    private void assertPublishedEvent(MobileOnboardingEventType expectedType) {
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, atLeastOnce()).publish(eventCaptor.capture());
        List<MobileOnboardingEventType> eventTypes = eventCaptor.getAllValues().stream()
                .filter(MobileOnboardingEvent.class::isInstance)
                .map(MobileOnboardingEvent.class::cast)
                .map(MobileOnboardingEvent::eventType)
                .toList();
        assertTrue(eventTypes.contains(expectedType), "Expected event not published: " + expectedType);
    }
}
