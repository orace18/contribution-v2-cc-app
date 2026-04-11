package com.ccapp.payments.subscription.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateRequest;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import com.ccapp.mobile.payments.service.MobilePaymentService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.dto.InitiateSubscriptionRequest;
import com.ccapp.payments.subscription.mapper.SubscriptionPlanMapper;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.wallet.service.WalletDebitFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();
    private static final UUID ACTOR_USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private SubscriptionPlanMapper subscriptionPlanMapper;
    @Mock
    private CountryContextService countryContextService;
    @Mock
    private CountryRepository countryRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private JawuPayPspClient jawuPayPspClient;
    @Mock
    private WalletDebitFacade walletDebitFacade;
    @Mock
    private MembershipService membershipService;
    @Mock
    private SubscriptionPaymentFinalizationService subscriptionPaymentFinalizationService;

    private SubscriptionService subscriptionService;
    private MobilePaymentService mobilePaymentService;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionPlanRepository,
                subscriptionPlanMapper,
                countryContextService,
                countryRepository,
                accessControlService,
                countryChartOfAccountsService
        );
        mobilePaymentService = new MobilePaymentService(
                mobileOnboardingJourneyService,
                operationRepository,
                jawuPayPspClient,
                subscriptionService,
                walletDebitFacade,
                membershipService,
                subscriptionPaymentFinalizationService
        );

        plan = SubscriptionPlan.builder()
                .id(PLAN_ID)
                .code("GOLD_1M")
                .name("Gold 1 month")
                .grade(SubscriptionGrade.GOLD)
                .durationMonths(1)
                .country("BJ")
                .currency("XOF")
                .price(new BigDecimal("5000.00"))
                .active(true)
                .build();

        when(countryContextService.resolve(ACCOUNT_HOLDER_ID))
                .thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryRepository.findByCode("BJ"))
                .thenReturn(Optional.of(Country.builder()
                        .code("BJ")
                        .currency("XOF")
                        .configured(true)
                        .active(true)
                        .fallback(false)
                        .build()));
        when(subscriptionPlanRepository.findByIdAndActiveTrue(PLAN_ID))
                .thenReturn(Optional.of(plan));
        when(countryChartOfAccountsService.findOrCreate(
                "BJ",
                CountryAccountGroup.MEMBERSHIP_FEES,
                "750001 Cotisations des membres"
        )).thenReturn(CountryChartAccount.builder().accountCode("750001").build());
    }

    @Test
    void preparePurchaseContext_returnsValidatedSubscriptionContext() {
        SubscriptionService.SubscriptionPurchaseContext context =
                subscriptionService.preparePurchaseContext(
                        ACCOUNT_HOLDER_ID,
                        ACTOR_USER_ID,
                        PLAN_ID,
                        "membership:test"
                );

        assertEquals(ACCOUNT_HOLDER_ID, context.accountHolderId());
        assertEquals(ACTOR_USER_ID, context.actorUserId());
        assertEquals(plan, context.plan());
        assertEquals("membership:test", context.idempotencyKey());
        assertEquals("750001", context.destinationAccountCode());
        assertEquals("Subscription GOLD_1M", context.description());
        assertEquals("subscription|" + PLAN_ID + "|5000|XOF", context.fingerprint());
        assertEquals("BJ", context.countryCode());
        verify(accessControlService).assertCanActOnAccountHolder(
                ACTOR_USER_ID,
                ACCOUNT_HOLDER_ID,
                com.ccapp.identity.delegation.domain.enums.PermissionLevel.TRANSACT
        );
    }

    @Test
    void mobilePaymentService_walletRouteUsesSharedPaymentLayerAndFinalizesSubscription() {
        Operation confirmedOperation = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .actorUserId(ACTOR_USER_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .planId(PLAN_ID)
                .amount(new BigDecimal("5000.00"))
                .currency("XOF")
                .idempotencyKey("membership:test")
                .build();
        MobilePaymentInitiateRequest request = new MobilePaymentInitiateRequest(
                "membership:test",
                "BJ",
                "CC_WALLET",
                null,
                null,
                "membership",
                PLAN_ID,
                "12345"
        );

        when(walletDebitFacade.executeSecureDebit(any()))
                .thenReturn(WalletDebitFacade.WalletDebitExecutionResult.confirmed(confirmedOperation));

        MobilePaymentInitiateResponse response = mobilePaymentService.initiate(
                ACTOR_USER_ID,
                ACCOUNT_HOLDER_ID,
                request
        );

        assertEquals(OPERATION_ID, response.operationId());
        assertEquals("CONFIRMED", response.status());

        ArgumentCaptor<WalletDebitFacade.SecureDebitRequest> captor =
                ArgumentCaptor.forClass(WalletDebitFacade.SecureDebitRequest.class);
        verify(walletDebitFacade).executeSecureDebit(captor.capture());
        WalletDebitFacade.SecureDebitRequest debitRequest = captor.getValue();
        assertEquals(OperationType.SUBSCRIPTION, debitRequest.operationType());
        assertEquals("12345", debitRequest.pin());
        assertEquals(WalletDebitFacade.SecureDebitPostingMode.OPERATION_POSTING, debitRequest.postingMode());
        assertEquals("BJ", debitRequest.requiredCountryCode());

        verify(membershipService).createPendingMembership(OPERATION_ID, ACCOUNT_HOLDER_ID, plan);
        verify(subscriptionPaymentFinalizationService).finalizeConfirmedOperation(OPERATION_ID);
        verify(mobileOnboardingJourneyService, never()).initiatePaidPlanPayment(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void mobilePaymentService_walletRouteMapsWalletErrors() {
        MobilePaymentInitiateRequest request = new MobilePaymentInitiateRequest(
                "membership:test",
                "BJ",
                "CC_WALLET",
                null,
                null,
                "membership",
                PLAN_ID,
                "12345"
        );

        when(walletDebitFacade.executeSecureDebit(any()))
                .thenThrow(new BusinessException(
                        "Insufficient available balance. Available: 0 XOF, requested: 5000 XOF"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                mobilePaymentService.initiate(ACTOR_USER_ID, ACCOUNT_HOLDER_ID, request)
        );

        assertEquals("INSUFFICIENT_BALANCE", exception.getMessage());
        verify(membershipService, never()).createPendingMembership(any(), any(), any());
        verify(subscriptionPaymentFinalizationService, never()).finalizeConfirmedOperation(any());
    }

    @Test
    void mobilePaymentService_walletRouteMapsInvalidPinErrors() {
        MobilePaymentInitiateRequest request = new MobilePaymentInitiateRequest(
                "membership:test",
                "BJ",
                "CC_WALLET",
                null,
                null,
                "membership",
                PLAN_ID,
                "12345"
        );

        when(walletDebitFacade.executeSecureDebit(any()))
                .thenThrow(new BusinessException("WALLET_PIN_INVALID"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                mobilePaymentService.initiate(ACTOR_USER_ID, ACCOUNT_HOLDER_ID, request)
        );

        assertEquals("INVALID_PIN", exception.getMessage());
        verify(membershipService, never()).createPendingMembership(any(), any(), any());
        verify(subscriptionPaymentFinalizationService, never()).finalizeConfirmedOperation(any());
    }

    @Test
    void mobilePaymentService_walletRouteSignalsOtpRequirement() {
        MobilePaymentInitiateRequest request = new MobilePaymentInitiateRequest(
                "membership:test",
                "BJ",
                "CC_WALLET",
                null,
                null,
                "membership",
                PLAN_ID,
                "12345"
        );

        when(walletDebitFacade.executeSecureDebit(any()))
                .thenReturn(WalletDebitFacade.WalletDebitExecutionResult.challengeRequired(
                        OPERATION_ID,
                        UUID.randomUUID(),
                        OffsetDateTime.now().plusMinutes(2)
                ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                mobilePaymentService.initiate(ACTOR_USER_ID, ACCOUNT_HOLDER_ID, request)
        );

        assertEquals("WALLET_OTP_REQUIRED", exception.getMessage());
        verify(membershipService, never()).createPendingMembership(any(), any(), any());
        verify(subscriptionPaymentFinalizationService, never()).finalizeConfirmedOperation(any());
    }

    @Test
    void mobilePaymentService_keepsPspDelegationUnchangedForMoovAndMtn() {
        MobilePaymentInitiateRequest request = new MobilePaymentInitiateRequest(
                "membership:test",
                "BJ",
                "MOBILE_MONEY_MTN",
                "22901020304",
                "MTN",
                "membership",
                null,
                null
        );
        MobilePaymentInitiateResponse expectedResponse = new MobilePaymentInitiateResponse(
                OPERATION_ID,
                "PENDING_PSP",
                "psp-1",
                "https://checkout"
        );

        when(mobileOnboardingJourneyService.initiatePaidPlanPayment(
                ACTOR_USER_ID,
                ACCOUNT_HOLDER_ID,
                "membership:test",
                "BJ",
                "MOBILE_MONEY_MTN",
                "22901020304",
                "MTN"
        )).thenReturn(expectedResponse);

        MobilePaymentInitiateResponse response = mobilePaymentService.initiate(
                ACTOR_USER_ID,
                ACCOUNT_HOLDER_ID,
                request
        );

        assertEquals(expectedResponse, response);
        verify(walletDebitFacade, never()).executeSecureDebit(any());
    }

    @Test
    void mobilePaymentService_keepsPspDelegationUnchangedForMoov() {
        MobilePaymentInitiateRequest request = new MobilePaymentInitiateRequest(
                "membership:test",
                "BJ",
                "MOBILE_MONEY_MOOV",
                "22901020304",
                "MOOV",
                "membership",
                null,
                null
        );
        MobilePaymentInitiateResponse expectedResponse = new MobilePaymentInitiateResponse(
                OPERATION_ID,
                "PENDING_PSP",
                "psp-2",
                "https://checkout-moov"
        );

        when(mobileOnboardingJourneyService.initiatePaidPlanPayment(
                ACTOR_USER_ID,
                ACCOUNT_HOLDER_ID,
                "membership:test",
                "BJ",
                "MOBILE_MONEY_MOOV",
                "22901020304",
                "MOOV"
        )).thenReturn(expectedResponse);

        MobilePaymentInitiateResponse response = mobilePaymentService.initiate(
                ACTOR_USER_ID,
                ACCOUNT_HOLDER_ID,
                request
        );

        assertEquals(expectedResponse, response);
        verify(walletDebitFacade, never()).executeSecureDebit(any());
    }

    @Test
    void legacySubscriptionInitiateIsGuardedWithSharedPaymentMessage() {
        InitiateSubscriptionRequest request = new InitiateSubscriptionRequest(
                PLAN_ID,
                "membership:test",
                "12345",
                null,
                null
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                subscriptionService.initiate(ACCOUNT_HOLDER_ID, ACTOR_USER_ID, request)
        );

        assertEquals(SubscriptionService.LEGACY_INITIATE_BLOCK_MESSAGE, exception.getMessage());
    }
}
