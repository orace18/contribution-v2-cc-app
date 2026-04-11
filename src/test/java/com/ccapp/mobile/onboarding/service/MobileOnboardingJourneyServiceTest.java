package com.ccapp.mobile.onboarding.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.financial.access.service.MemberFinancialProfileService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.dto.OnboardingStatusResponse;
import com.ccapp.identity.service.IdentityOnboardingService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.verification.domain.entity.PhoneVerification;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
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
import com.ccapp.payments.psp.service.JawuPayPaymentOrchestrationService;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.service.WalletLifecycleService;
import com.ccapp.wallet.service.WalletState;
import com.ccapp.wallet.service.WalletStatusSnapshot;
import com.ccapp.wallet.service.WalletStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

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
import static org.mockito.ArgumentMatchers.argThat;
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
    private JawuPayPaymentOrchestrationService jawuPayPaymentOrchestrationService;
    @Mock
    private ObjectProvider<JawuPayWebhookService> jawuPayWebhookServiceProvider;
    @Mock
    private JawuPayWebhookService jawuPayWebhookService;
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
    @Mock
    private IdentityOnboardingService identityOnboardingService;
    @Mock
    private FinancialAccessPolicy financialAccessPolicy;
    @Mock
    private MemberFinancialProfileService memberFinancialProfileService;
    @Mock
    private PhoneVerificationRepository phoneVerificationRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private WalletLifecycleService walletLifecycleService;
    @Mock
    private WalletStatusService walletStatusService;

    @InjectMocks
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @BeforeEach
    void setUp() {
        when(jawuPayWebhookServiceProvider.getObject()).thenReturn(jawuPayWebhookService);
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
        when(identityOnboardingService.getOnboardingStatus(ACCOUNT_ID)).thenReturn(identityCompleted());
        when(mlmGraphSyncService.isNetworkReady(ACCOUNT_ID)).thenReturn(true);
        when(mlmMemberLinkRepository.existsById(ACCOUNT_ID)).thenReturn(false);
        when(mlmMemberLinkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberFinancialProfileService.getProfile(ACCOUNT_ID))
                .thenReturn(financialProfile(false, MembershipStatus.PENDING, false, null, List.of(
                        FinancialAccessReasonCode.MEMBERSHIP_NOT_ACTIVE
                )));
        when(walletLifecycleService.resolveStatus(ACCOUNT_ID))
                .thenReturn(walletStatusSnapshot(
                        WalletState.PHONE_NOT_VERIFIED,
                        false,
                        false,
                        "Phone verification required for wallet activation",
                        List.of(FinancialAccessReasonCode.PHONE_NOT_VERIFIED)
                ));
        when(walletLifecycleService.activateIfEligible(eq(ACCOUNT_ID), any()))
                .thenReturn(Optional.empty());
        when(operationService.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(operationRepository.findFirstByAccountHolderIdAndTypeAndPlanIdAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void freePlanFlow_shouldActivateMembershipWithoutCompletingOnboardingBeforePhoneVerification() {
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
        when(memberFinancialProfileService.getProfile(ACCOUNT_ID))
                .thenReturn(financialProfile(true, MembershipStatus.ACTIVE, false, null, List.of()));
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false, true);

        var response = mobileOnboardingJourneyService.activateFreePlan(ACTOR_ID, ACCOUNT_ID);

        assertEquals("ACTIVE_MEMBER", response.journeyStatus());
        assertTrue(response.active());
        verify(mlmPlacementService).placeAccountHolder(ACTOR_ID, ACCOUNT_ID, SPONSOR_ID);
        assertPublishedEvent(MobileOnboardingEventType.MEMBERSHIP_ACTIVATED);
        assertPublishedEvent(MobileOnboardingEventType.NETWORK_LINKED);
        verify(domainEventPublisher, never()).publish(argThat(event ->
                event instanceof MobileOnboardingEvent mobileEvent
                        && mobileEvent.eventType() == MobileOnboardingEventType.ONBOARDING_COMPLETED));
    }

    @Test
    void paidPlanFlow_shouldInitiateThenRequirePhoneVerificationBeforeCompletion() {
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
        Operation pending = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-001")
                .redirectUrl("https://checkout")
                .planId(PLAN_ID)
                .build();
        when(jawuPayPaymentOrchestrationService.initiatePayment(
                eq(ACCOUNT_ID), eq(ACTOR_ID),
                eq(new BigDecimal("15000.0000")), eq("XOF"), eq("BJ"),
                eq("MOBILE_MONEY_MTN"), eq("22961000000"), eq("MTN"),
                eq("idem-001"), any(), eq(OperationType.SUBSCRIPTION), eq(PLAN_ID)
        )).thenReturn(new JawuPayPaymentOrchestrationService.InitiatePaymentResult(OPERATION_ID, "https://checkout"));
        when(operationService.getById(OPERATION_ID)).thenReturn(pending);
        when(membershipService.createPendingMembership(OPERATION_ID, ACCOUNT_ID, paidPlan))
                .thenReturn(Membership.builder().status(MembershipStatus.PENDING).build());
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false, true);

        MobilePaymentInitiateResponse paymentResponse = mobileOnboardingJourneyService
                .initiatePaidPlanPayment(
                        ACTOR_ID, ACCOUNT_ID, "idem-001",
                        "BJ", "MOBILE_MONEY_MTN", "22961000000", "MTN"
                );

        assertEquals("PENDING_PSP", paymentResponse.status());
        assertEquals("PSP-REF-001", paymentResponse.pspReference());
        assertEquals("https://checkout", paymentResponse.checkoutUrl());

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

        assertEquals(MobileOnboardingJourneyStatus.ACTIVE_MEMBER, journey.getStatus());
        when(walletLifecycleService.activateIfEligible(ACCOUNT_ID, "22961000000"))
                .thenReturn(Optional.of(Wallet.builder()
                        .id(UUID.randomUUID())
                        .accountHolderId(ACCOUNT_ID)
                        .provider("MOBILE")
                        .mobileMoneyNumber("22961000000")
                        .status("ACTIVE")
                        .walletType(WalletType.USER)
                        .build()));

        mobileOnboardingJourneyService.onPhoneVerified(ACCOUNT_ID, "22961000000");

        assertEquals(MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED, journey.getStatus());
        assertPublishedEvent(MobileOnboardingEventType.PAYMENT_INITIATED);
        assertPublishedEvent(MobileOnboardingEventType.PAYMENT_CONFIRMED);
        assertPublishedEvent(MobileOnboardingEventType.PHONE_VERIFIED);
        assertPublishedEvent(MobileOnboardingEventType.WALLET_ACTIVATED);
        assertPublishedEvent(MobileOnboardingEventType.ONBOARDING_COMPLETED);
    }

    @Test
    void paidPlanFlow_reentryPending_shouldReturnExistingCheckoutUrlWithoutNewPspCall() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PLAN_SELECTED, true, SPONSOR_ID, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        SubscriptionPlan paidPlan = plan(SubscriptionGrade.CLASSIC, new BigDecimal("15000.0000"));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID)).thenReturn(Optional.of(paidPlan));

        Operation pending = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-REENTRY")
                .redirectUrl("https://checkout/reentry")
                .planId(PLAN_ID)
                .build();
        when(operationRepository.findFirstByAccountHolderIdAndTypeAndPlanIdAndStatusInOrderByCreatedAtDesc(
                eq(ACCOUNT_ID),
                eq(OperationType.SUBSCRIPTION),
                eq(PLAN_ID),
                any()
        )).thenReturn(Optional.of(pending));
        when(operationService.getById(OPERATION_ID)).thenReturn(pending);
        when(membershipService.createPendingMembership(OPERATION_ID, ACCOUNT_ID, paidPlan))
                .thenReturn(Membership.builder().status(MembershipStatus.PENDING).build());

        MobilePaymentInitiateResponse response = mobileOnboardingJourneyService.initiatePaidPlanPayment(
                ACTOR_ID, ACCOUNT_ID, "idem-reentry", "BJ", "MOBILE_MONEY_MTN", "22961000000", "MTN"
        );

        assertEquals("PENDING_PSP", response.status());
        assertEquals("PSP-REF-REENTRY", response.pspReference());
        assertEquals("https://checkout/reentry", response.checkoutUrl());
        assertTrue(response.duplicateRequest());
        assertEquals(OPERATION_ID, response.existingOperationId());
        assertEquals("PENDING_PSP", response.existingStatus());
        assertEquals("A payment operation for this subscription is already in progress.", response.message());
        verify(jawuPayPaymentOrchestrationService, never())
                .initiatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PSP-REF-REENTRY");
    }

    @Test
    void paidPlanFlow_sameIdempotencyKey_shouldReturnExistingOperationWithoutNewPspCall() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PLAN_SELECTED, true, SPONSOR_ID, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        SubscriptionPlan paidPlan = plan(SubscriptionGrade.CLASSIC, new BigDecimal("15000.0000"));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID)).thenReturn(Optional.of(paidPlan));

        Operation confirmed = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .idempotencyKey("idem-existing")
                .pspReference("PSP-REF-CONFIRMED")
                .redirectUrl("https://checkout/confirmed")
                .planId(PLAN_ID)
                .build();
        when(operationService.findByIdempotencyKey("idem-existing")).thenReturn(Optional.of(confirmed));

        MobilePaymentInitiateResponse response = mobileOnboardingJourneyService.initiatePaidPlanPayment(
                ACTOR_ID, ACCOUNT_ID, "idem-existing", "BJ", "MOBILE_MONEY_MTN", "22961000000", "MTN"
        );

        assertEquals("CONFIRMED", response.status());
        assertTrue(response.duplicateRequest());
        assertEquals(OPERATION_ID, response.existingOperationId());
        assertEquals("CONFIRMED", response.existingStatus());
        assertEquals("This subscription payment is already confirmed.", response.message());
        verify(jawuPayPaymentOrchestrationService, never())
                .initiatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jawuPayWebhookService, never()).checkAndApplyPaymentStatus(any());
    }

    @Test
    void paidPlanFlow_recoveredFailedExistingOperation_shouldCreateNewPspOperation() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.PLAN_SELECTED, true, SPONSOR_ID, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        SubscriptionPlan paidPlan = plan(SubscriptionGrade.CLASSIC, new BigDecimal("15000.0000"));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID)).thenReturn(Optional.of(paidPlan));

        UUID oldOperationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID newOperationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Operation pending = Operation.builder()
                .id(oldOperationId)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-OLD")
                .planId(PLAN_ID)
                .build();
        Operation failed = Operation.builder()
                .id(oldOperationId)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.FAILED)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-OLD")
                .planId(PLAN_ID)
                .build();
        when(operationRepository.findFirstByAccountHolderIdAndTypeAndPlanIdAndStatusInOrderByCreatedAtDesc(
                eq(ACCOUNT_ID),
                eq(OperationType.SUBSCRIPTION),
                eq(PLAN_ID),
                any()
        )).thenReturn(Optional.of(pending));
        when(operationService.getById(oldOperationId)).thenReturn(failed);

        Operation newPending = Operation.builder()
                .id(newOperationId)
                .accountHolderId(ACCOUNT_ID)
                .actorUserId(ACTOR_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .pspReference("PSP-REF-NEW")
                .redirectUrl("https://checkout/new")
                .planId(PLAN_ID)
                .build();
        when(jawuPayPaymentOrchestrationService.initiatePayment(
                eq(ACCOUNT_ID), eq(ACTOR_ID),
                eq(new BigDecimal("15000.0000")), eq("XOF"), eq("BJ"),
                eq("MOBILE_MONEY_MTN"), eq("22961000000"), eq("MTN"),
                any(), any(), eq(OperationType.SUBSCRIPTION), eq(PLAN_ID)
        )).thenReturn(new JawuPayPaymentOrchestrationService.InitiatePaymentResult(newOperationId, "https://checkout/new"));
        when(operationService.getById(newOperationId)).thenReturn(newPending);
        when(membershipService.createPendingMembership(newOperationId, ACCOUNT_ID, paidPlan))
                .thenReturn(Membership.builder().status(MembershipStatus.PENDING).build());

        MobilePaymentInitiateResponse response = mobileOnboardingJourneyService.initiatePaidPlanPayment(
                ACTOR_ID, ACCOUNT_ID, null, "BJ", "MOBILE_MONEY_MTN", "22961000000", "MTN"
        );

        assertEquals(newOperationId, response.operationId());
        assertFalse(response.duplicateRequest());
        assertEquals("PENDING_PSP", response.status());
        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PSP-REF-OLD");
        verify(jawuPayPaymentOrchestrationService)
                .initiatePayment(eq(ACCOUNT_ID), eq(ACTOR_ID), any(), any(), any(), any(), any(), any(), any(), any(), eq(OperationType.SUBSCRIPTION), eq(PLAN_ID));
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
    void selectCountry_shouldNotRequirePhoneVerificationOrWalletActivation() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.AUTHENTICATED, false, null, null);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        when(accountHolderService.selectCountry(ACCOUNT_ID, "BJ"))
                .thenReturn(AccountHolder.builder().id(ACCOUNT_ID).countryCode("BJ").build());

        var response = mobileOnboardingJourneyService.selectCountry(ACTOR_ID, ACCOUNT_ID, "BJ");

        assertEquals("BJ", response.countryCode());
        assertEquals(MobileOnboardingJourneyStatus.COUNTRY_SELECTED, journey.getStatus());
    }

    @Test
    void onPlanSelected_shouldNotRequireSponsorStep() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.COUNTRY_SELECTED, false, null, null);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));

        mobileOnboardingJourneyService.onPlanSelected(ACCOUNT_ID, plan(SubscriptionGrade.CLASSIC, new BigDecimal("15000.0000")));

        assertEquals(MobileOnboardingJourneyStatus.PLAN_SELECTED, journey.getStatus());
        assertEquals(PLAN_ID, journey.getPlanId());
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
        assertEquals(MobileOnboardingJourneyStatus.ACTIVE_MEMBER, journey.getStatus());
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

    @Test
    void getStatus_shouldKeepBlockingJourneyStepWhenPhoneIsVerifiedBeforePlanSelection() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.COUNTRY_SELECTED, false, null, null);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        when(accountHolderService.getById(ACCOUNT_ID)).thenReturn(AccountHolder.builder()
                .id(ACCOUNT_ID)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .build());
        when(phoneVerificationRepository.findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                ACCOUNT_ID,
                PhoneVerificationStatus.VERIFIED
        )).thenReturn(Optional.of(PhoneVerification.builder()
                .id(UUID.randomUUID())
                .requesterAccountHolderId(ACCOUNT_ID)
                .phoneNumber("22961000000")
                .channel(PhoneVerificationChannel.SMS)
                .status(PhoneVerificationStatus.VERIFIED)
                .codeHash("hash")
                .attemptCount(0)
                .maxAttempts(5)
                .sendCount(1)
                .maxSendCount(3)
                .lastSentAt(OffsetDateTime.now())
                .resendAvailableAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .verifiedAt(OffsetDateTime.now())
                .build()));
        when(memberFinancialProfileService.getProfile(ACCOUNT_ID))
                .thenReturn(financialProfile(false, null, false, null, List.of(FinancialAccessReasonCode.MEMBERSHIP_MISSING)));
        when(walletLifecycleService.resolveStatus(ACCOUNT_ID))
                .thenReturn(walletStatusSnapshot(
                        WalletState.PHONE_NOT_VERIFIED,
                        true,
                        false,
                        "Phone verification required for wallet activation",
                        List.of(FinancialAccessReasonCode.PHONE_NOT_VERIFIED)
                ));
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false);

        MobileOnboardingStatusResponse response = mobileOnboardingJourneyService.getStatus(ACCOUNT_ID);

        assertEquals("COUNTRY_SELECTED", response.currentStatus());
        assertNotNull(response.nextStep());
        assertEquals("SELECT_PLAN", response.nextStep().code());
        assertFalse(response.isActive());
        assertTrue(response.completedSteps().contains("PHONE_VERIFIED"));
    }

    @Test
    void getStatus_shouldAlignWithWalletStatusWhenWalletActivationIsPending() {
        MobileOnboardingJourney journey = journey(MobileOnboardingJourneyStatus.ACTIVE_MEMBER, false, null, PLAN_ID);
        when(journeyRepository.findByAccountHolderId(ACCOUNT_ID)).thenReturn(Optional.of(journey));
        when(accountHolderService.getById(ACCOUNT_ID)).thenReturn(AccountHolder.builder()
                .id(ACCOUNT_ID)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .build());
        when(memberFinancialProfileService.getProfile(ACCOUNT_ID))
                .thenReturn(financialProfile(true, MembershipStatus.ACTIVE, false, null, List.of()));
        when(walletLifecycleService.resolveStatus(ACCOUNT_ID))
                .thenReturn(walletStatusSnapshot(
                        WalletState.READY_FOR_WALLET_CREATION,
                        true,
                        false,
                        "Wallet is ready for creation",
                        List.of()
                ));
        when(mlmRelationshipRepository.existsByDescendantId(ACCOUNT_ID)).thenReturn(false);

        MobileOnboardingStatusResponse response = mobileOnboardingJourneyService.getStatus(ACCOUNT_ID);

        assertEquals("PHONE_VERIFIED", response.currentStatus());
        assertNotNull(response.nextStep());
        assertEquals("ACTIVATE_WALLET", response.nextStep().code());
        assertEquals("/wallet/activate", response.nextStep().endpoint());
        assertEquals("Wallet is ready for creation", response.nextStep().description());
        assertTrue(response.isActive());
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

    private OnboardingStatusResponse identityCompleted() {
        return new OnboardingStatusResponse(
                ACCOUNT_ID,
                OnboardingStep.COMPLETED,
                3,
                3,
                new BigDecimal("100.00"),
                true,
                true,
                true,
                true,
                OffsetDateTime.now(),
                null
        );
    }

    private MemberFinancialProfile financialProfile(boolean membershipActive,
                                                    MembershipStatus membershipStatus,
                                                    boolean walletActive,
                                                    String walletStatus,
                                                    List<FinancialAccessReasonCode> blockingReasons) {
        return new MemberFinancialProfile(
                ACCOUNT_ID,
                null,
                null,
                membershipStatus,
                membershipActive ? OffsetDateTime.now().plusDays(30) : null,
                membershipActive,
                walletStatus,
                walletActive,
                false,
                blockingReasons.isEmpty() ? FinancialAccessState.FINANCIAL_ALLOWED : FinancialAccessState.FINANCIAL_DENIED,
                false,
                "NOT_CONFIGURED",
                blockingReasons
        );
    }

    private WalletStatusSnapshot walletStatusSnapshot(
            WalletState state,
            boolean phoneVerified,
            boolean active,
            String message,
            List<FinancialAccessReasonCode> blockingReasons) {
        return new WalletStatusSnapshot(
                active ? UUID.randomUUID() : null,
                active,
                false,
                state,
                message,
                phoneVerified,
                state == WalletState.READY_FOR_WALLET_CREATION || state == WalletState.ACTIVE,
                blockingReasons
        );
    }
}
