package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.domain.entity.JawuPayWebhookEvent;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.repository.JawuPayWebhookEventRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletWithdrawalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JawuPayWebhookServiceTest {

    @Mock OperationService operationService;
    @Mock JawuPayPspClient pspClient;
    @Mock AccountingPostingService accountingPostingService;
    @Mock MlmCommissionService mlmCommissionService;
    @Mock MembershipService membershipService;
    @Mock MobileOnboardingJourneyService mobileOnboardingJourneyService;
    @Mock WalletDepositService walletDepositService;
    @Mock WalletWithdrawalService walletWithdrawalService;
    @Mock JawuPayWebhookEventRepository webhookEventRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    JawuPayWebhookService service;

    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final String PAYMENT_ID = UUID.randomUUID().toString();
    private static final String PROVIDER_REFERENCE = "PROVIDER-REF-001";
    private static final String PAYOUT_ID = UUID.randomUUID().toString();
    private static final String WEBHOOK_SECRET = "jawupay-webhook-test-secret";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "allowedIpsConfig", "127.0.0.1");
        ReflectionTestUtils.setField(service, "webhookHmacSecret", WEBHOOK_SECRET);
        service.init();
        when(webhookEventRepository.save(any(JawuPayWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void init_allowlistEmpty_failsStartup() {
        ReflectionTestUtils.setField(service, "allowedIpsConfig", "");

        assertThatThrownBy(() -> service.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowedIps");
    }

    @Test
    void processWebhook_ipNotInAllowlist_throwsAccessDeniedException() {
        JawuPayWebhookRequest request = request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null);

        SignedWebhook signedWebhook = signedWebhook(request);

        assertThatThrownBy(() -> service.processWebhook(
                request,
                signedWebhook.rawRequestBody(),
                signedWebhook.signature(),
                "1.2.3.4"
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("unauthorized IP");
    }

    @Test
    void processWebhook_validSignature_accepted() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        processWebhook(request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null), "127.0.0.1");

        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
    }

    @Test
    void processWebhook_invalidSignature_rejected() {
        JawuPayWebhookRequest request = request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null);
        SignedWebhook signedWebhook = signedWebhook(request);

        assertThatThrownBy(() -> service.processWebhook(
                request,
                signedWebhook.rawRequestBody(),
                "deadbeef",
                "127.0.0.1"
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid JawuPay webhook signature");

        verifyNoInteractions(operationService, webhookEventRepository, walletDepositService);
    }

    @Test
    void processWebhook_tamperedPayloadRejected() throws Exception {
        JawuPayWebhookRequest request = request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null);
        SignedWebhook signedWebhook = signedWebhook(request);
        String tamperedJson = new String(signedWebhook.rawRequestBody(), StandardCharsets.UTF_8)
                .replace("\"amount\":150000", "\"amount\":150001");
        JawuPayWebhookRequest tamperedRequest = objectMapper.readValue(tamperedJson, JawuPayWebhookRequest.class);

        assertThatThrownBy(() -> service.processWebhook(
                tamperedRequest,
                tamperedJson.getBytes(StandardCharsets.UTF_8),
                signedWebhook.signature(),
                "127.0.0.1"
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid JawuPay webhook signature");

        verifyNoInteractions(operationService, webhookEventRepository, walletDepositService);
    }

    @Test
    void processWebhook_invalidProvider_rejected() {
        JawuPayWebhookRequest request = request("OTHER", "SUCCESS", PAYMENT_ID, null, null);

        SignedWebhook signedWebhook = signedWebhook(request);

        assertThatThrownBy(() -> service.processWebhook(
                request,
                signedWebhook.rawRequestBody(),
                signedWebhook.signature(),
                "127.0.0.1"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only JawuPay provider");
    }

    @Test
    void processWebhook_duplicateEvent_rejected() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(webhookEventRepository.save(any(JawuPayWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        JawuPayWebhookRequest request = request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null);
        SignedWebhook signedWebhook = signedWebhook(request);

        assertThatThrownBy(() -> service.processWebhook(
                request,
                signedWebhook.rawRequestBody(),
                signedWebhook.signature(),
                "127.0.0.1"
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Duplicate JawuPay webhook event");
    }

    @Test
    void processWebhook_amountMismatch_rejected() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        JawuPayWebhookRequest request = new JawuPayWebhookRequest(
                "JAWUPAY",
                "SUCCESS",
                PAYMENT_ID,
                null,
                null,
                null,
                OPERATION_ID,
                200000L,
                "XOF",
                "evt-amount-mismatch"
        );

        SignedWebhook signedWebhook = signedWebhook(request);

        assertThatThrownBy(() -> service.processWebhook(
                request,
                signedWebhook.rawRequestBody(),
                signedWebhook.signature(),
                "127.0.0.1"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("amount mismatch");
    }

    @Test
    void processWebhook_missingOperationId_acceptsWhenIdentifiersMatch() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        JawuPayWebhookRequest request = new JawuPayWebhookRequest(
                "JAWUPAY",
                "SUCCESS",
                PAYMENT_ID,
                null,
                null,
                null,
                null,
                150000L,
                "XOF",
                "evt-operation-optional"
        );

        processWebhook(request, "127.0.0.1");

        verify(operationService).confirmOperation(OPERATION_ID);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
    }

    @Test
    void processWebhook_operationIdMismatch_rejected() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        JawuPayWebhookRequest request = new JawuPayWebhookRequest(
                "JAWUPAY",
                "SUCCESS",
                PAYMENT_ID,
                null,
                null,
                null,
                UUID.randomUUID(),
                150000L,
                "XOF",
                "evt-operation-mismatch"
        );

        SignedWebhook signedWebhook = signedWebhook(request);

        assertThatThrownBy(() -> service.processWebhook(
                request,
                signedWebhook.rawRequestBody(),
                signedWebhook.signature(),
                "127.0.0.1"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("operationId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "CONFIRMED", "PAID", "COMPLETED"})
    void processWebhook_successStatuses_confirmAndDispatchToDepositService(String rawStatus) {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        processWebhook(request("JAWUPAY", rawStatus, PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService).confirmOperation(OPERATION_ID);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
        verifyNoInteractions(walletWithdrawalService, accountingPostingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED"})
    void processWebhook_failureStatuses_markFailed(String rawStatus) {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        processWebhook(request("JAWUPAY", rawStatus, PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService).markFailed(OPERATION_ID);
        verifyNoInteractions(walletDepositService, accountingPostingService);
    }

    @Test
    void processWebhook_pendingStatus_noStateChange() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        processWebhook(request("JAWUPAY", "PENDING", PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService, never()).confirmOperation(any());
        verify(operationService, never()).markFailed(any());
    }

    @Test
    void processWebhook_alreadyConfirmed_idempotentNoAction() {
        Operation op = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        processWebhook(request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService, never()).confirmOperation(any());
    }

    @Test
    void processWebhook_providerReferenceFallback_recordsStructuredMetadata() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.empty());
        when(operationService.findByProviderReferenceOrPspReference(PROVIDER_REFERENCE)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        processWebhook(request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, PROVIDER_REFERENCE), "127.0.0.1");

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo(PROVIDER_REFERENCE);
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("SUCCESS");
        assertThat(metadataCaptor.getValue().pspPayload()).contains(PROVIDER_REFERENCE);
        verify(operationService).findByProviderReferenceOrPspReference(PROVIDER_REFERENCE);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
    }

    @Test
    void processWebhook_payoutId_usesPayoutLookupAndDispatchesWithdrawal() {
        Operation op = pendingPspOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.findByPayoutIdOrPspReference(PAYOUT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        processWebhook(request("JAWUPAY", "SUCCESS", null, PAYOUT_ID, "PAYOUT-REF-9"), "127.0.0.1");

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().payoutId()).isEqualTo(PAYOUT_ID);
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("PAYOUT-REF-9");
        verify(walletWithdrawalService).confirmWithdrawal(OPERATION_ID);
    }

    @Test
    void processWebhook_subscriptionSuccess_dispatchesMembershipLedgerMlmAndOnboarding() {
        Operation op = pendingPspOperation(OperationType.SUBSCRIPTION);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.SUBSCRIPTION);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        processWebhook(request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, PROVIDER_REFERENCE), "127.0.0.1");

        verify(operationService).confirmOperation(OPERATION_ID);
        verify(membershipService).activateForConfirmedOperation(confirmed);
        verify(accountingPostingService).postConfirmedOperation(OPERATION_ID);
        verify(mlmCommissionService).calculateForOperation(confirmed);
        verify(mobileOnboardingJourneyService).onPaidPaymentConfirmed(confirmed);
        verifyNoInteractions(walletDepositService, walletWithdrawalService);
    }

    @Test
    void processWebhook_subscriptionFailure_dispatchesFailureSideEffects() {
        Operation op = pendingPspOperation(OperationType.SUBSCRIPTION);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation failed = failedOperation(OperationType.SUBSCRIPTION);
        when(operationService.markFailed(OPERATION_ID)).thenReturn(failed);

        processWebhook(request("JAWUPAY", "FAILED", PAYMENT_ID, null, PROVIDER_REFERENCE), "127.0.0.1");

        verify(operationService).markFailed(OPERATION_ID);
        verify(membershipService).cancelPendingMembership(OPERATION_ID);
        verify(mobileOnboardingJourneyService).onPaidPaymentFailed(failed);
        verifyNoInteractions(walletDepositService, walletWithdrawalService, accountingPostingService, mlmCommissionService);
    }

    @Test
    void processWebhook_withdrawalFailure_dispatchesFailureHook() {
        Operation op = pendingPspOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.findByPayoutIdOrPspReference(PAYOUT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation failed = failedOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.markFailed(OPERATION_ID)).thenReturn(failed);

        processWebhook(request("JAWUPAY", "FAILED", null, PAYOUT_ID, "PAYOUT-REF-9"), "127.0.0.1");

        verify(operationService).markFailed(OPERATION_ID);
        verify(walletWithdrawalService).handlePspWithdrawalFailure(OPERATION_ID);
        verifyNoInteractions(walletDepositService, accountingPostingService, membershipService, mlmCommissionService, mobileOnboardingJourneyService);
    }

    @Test
    void processWebhook_processingFailure_marksReconciliationNeededAndDoesNotThrow() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);
        when(operationService.confirmOperation(OPERATION_ID)).thenThrow(new RuntimeException("ledger unavailable"));

        assertThatCode(() -> processWebhook(
                request("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null),
                "127.0.0.1"
        )).doesNotThrowAnyException();

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService, times(2)).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getAllValues().get(1).pspStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(metadataCaptor.getAllValues().get(1).pspPayload()).contains("ledger unavailable");
    }

    @Test
    void checkAndApplyPaymentStatus_amountMismatch_throwsBusinessException() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        JawuPayPaymentResponse payment = new JawuPayPaymentResponse(
                UUID.fromString(PAYMENT_ID), null, "SUCCESS",
                200000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", null,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        assertThatThrownBy(() -> service.checkAndApplyPaymentStatus(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("amount mismatch");
    }

    @Test
    void checkAndApplyPaymentStatus_matchingAmount_appliesStatusAndPersistsMetadata() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        JawuPayPaymentResponse payment = new JawuPayPaymentResponse(
                UUID.fromString(PAYMENT_ID), null, "SUCCESS",
                150000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", PROVIDER_REFERENCE,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.checkAndApplyPaymentStatus(PAYMENT_ID);

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo(PROVIDER_REFERENCE);
        assertThat(metadataCaptor.getValue().paymentMethod()).isEqualTo("MOBILE_MONEY_MTN");
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("SUCCESS");
        verify(operationService).confirmOperation(OPERATION_ID);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
    }

    @Test
    void checkAndApplyPaymentStatus_providerReferenceFallback_resolvesOperationAndCallsPsp() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        op.setPaymentId(null);
        op.setProviderReference(PROVIDER_REFERENCE);
        op.setPspReference(PROVIDER_REFERENCE);
        when(operationService.findByPaymentIdOrPspReference(PROVIDER_REFERENCE)).thenReturn(Optional.empty());
        when(operationService.findByProviderReferenceOrPspReference(PROVIDER_REFERENCE)).thenReturn(Optional.of(op));

        JawuPayPaymentResponse payment = new JawuPayPaymentResponse(
                UUID.fromString(PAYMENT_ID), null, "SUCCESS",
                150000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", PROVIDER_REFERENCE,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayment(PROVIDER_REFERENCE)).thenReturn(payment);
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.checkAndApplyPaymentStatus(PROVIDER_REFERENCE);

        verify(pspClient).getPayment(PROVIDER_REFERENCE);
        verify(operationService).findByProviderReferenceOrPspReference(PROVIDER_REFERENCE);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
    }

    @Test
    void markOperationAsTimedOut_marksFailedWithoutCreditingWallet() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);

        Operation failed = failedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);
        when(operationService.markFailed(OPERATION_ID)).thenReturn(failed);

        service.markOperationAsTimedOut(op);

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("TIMEOUT");
        verify(operationService).markFailed(OPERATION_ID);
        verifyNoInteractions(walletDepositService, accountingPostingService);
    }

    @Test
    void checkAndApplyPayoutStatus_matchingAmount_appliesStatusAndPersistsMetadata() {
        Operation op = pendingPspOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.findByPayoutIdOrPspReference(PAYOUT_ID)).thenReturn(Optional.of(op));

        JawuPayPayoutDetailsResponse payout = new JawuPayPayoutDetailsResponse(
                UUID.fromString(PAYOUT_ID), null, "SUCCESS",
                150000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", "PAYOUT-REF-9",
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayout(PAYOUT_ID)).thenReturn(payout);
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        service.checkAndApplyPayoutStatus(PAYOUT_ID);

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().payoutId()).isEqualTo(PAYOUT_ID);
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("PAYOUT-REF-9");
        assertThat(metadataCaptor.getValue().paymentMethod()).isEqualTo("MOBILE_MONEY_MTN");
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("SUCCESS");
        verify(walletWithdrawalService).confirmWithdrawal(OPERATION_ID);
    }

    private JawuPayWebhookRequest request(String provider,
                                          String status,
                                          String paymentId,
                                          String payoutId,
                                          String providerReference) {
        return new JawuPayWebhookRequest(
                provider,
                status,
                paymentId,
                payoutId,
                null,
                providerReference,
                OPERATION_ID,
                150000L,
                "XOF",
                UUID.randomUUID().toString()
        );
    }

    private void processWebhook(JawuPayWebhookRequest request, String remoteIp) {
        SignedWebhook signedWebhook = signedWebhook(request);
        service.processWebhook(request, signedWebhook.rawRequestBody(), signedWebhook.signature(), remoteIp);
    }

    private SignedWebhook signedWebhook(JawuPayWebhookRequest request) {
        try {
            byte[] rawRequestBody = objectMapper.writeValueAsBytes(request);
            return new SignedWebhook(rawRequestBody, hexEncode(sign(rawRequestBody)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build signed webhook payload", ex);
        }
    }

    private byte[] sign(byte[] payload) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload);
    }

    private String hexEncode(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record SignedWebhook(byte[] rawRequestBody, String signature) {}

    private Operation pendingPspOperation(OperationType type) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setStatus(OperationStatus.PENDING_PSP);
        op.setType(type);
        op.setAmount(new BigDecimal("1500.00"));
        op.setCurrency("XOF");
        return op;
    }

    private Operation confirmedOperation(OperationType type) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setStatus(OperationStatus.CONFIRMED);
        op.setType(type);
        op.setAmount(new BigDecimal("1500.00"));
        op.setCurrency("XOF");
        return op;
    }

    private Operation failedOperation(OperationType type) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setStatus(OperationStatus.FAILED);
        op.setType(type);
        op.setAmount(new BigDecimal("1500.00"));
        op.setCurrency("XOF");
        return op;
    }
}
