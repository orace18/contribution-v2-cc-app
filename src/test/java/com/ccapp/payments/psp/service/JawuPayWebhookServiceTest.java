package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
class JawuPayWebhookServiceTest {

    @Mock OperationService operationService;
    @Mock JawuPayPspClient pspClient;
    @Mock AccountingPostingService accountingPostingService;
    @Mock WalletDepositService walletDepositService;
    @Mock WalletWithdrawalService walletWithdrawalService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    JawuPayWebhookService service;

    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final String PAYMENT_ID = UUID.randomUUID().toString();
    private static final String PROVIDER_REFERENCE = "PROVIDER-REF-001";
    private static final String PAYOUT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "allowedIpsConfig", "");
        service.init();
    }

    @Test
    void processWebhook_ipNotInAllowlist_throwsAccessDeniedException() {
        ReflectionTestUtils.setField(service, "allowedIpsConfig", "10.0.0.1,10.0.0.2");
        service.init();

        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null);

        assertThatThrownBy(() -> service.processWebhook(request, "1.2.3.4"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("unauthorized IP");
    }

    @Test
    void processWebhook_allowlistEmpty_permitsAnyIp() {
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID))
                .thenReturn(Optional.empty());

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null), "9.9.9.9");

        verify(operationService).findByPaymentIdOrPspReference(PAYMENT_ID);
        verify(operationService, never()).recordPspMetadata(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "CONFIRMED", "PAID", "COMPLETED"})
    void processWebhook_successStatuses_confirmAndDispatchToDepositService(String rawStatus) {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", rawStatus, PAYMENT_ID, null, null), "127.0.0.1");

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

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", rawStatus, PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService).markFailed(OPERATION_ID);
        verifyNoInteractions(walletDepositService, accountingPostingService);
    }

    @Test
    void processWebhook_pendingStatus_noStateChange() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "PENDING", PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService, never()).confirmOperation(any());
        verify(operationService, never()).markFailed(any());
    }

    @Test
    void processWebhook_alreadyConfirmed_idempotentNoAction() {
        Operation op = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService, never()).confirmOperation(any());
    }

    @Test
    void processWebhook_unknownIdentifiers_logsAndReturns() {
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.empty());

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null), "127.0.0.1");

        verify(operationService).findByPaymentIdOrPspReference(PAYMENT_ID);
        verify(operationService, never()).recordPspMetadata(any(), any());
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

        service.processWebhook(
                new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null, PROVIDER_REFERENCE),
                "127.0.0.1"
        );

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

        Operation confirmed = confirmedOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.processWebhook(
                new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", null, PAYOUT_ID, "PAYOUT-REF-9"),
                "127.0.0.1"
        );

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().payoutId()).isEqualTo(PAYOUT_ID);
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("PAYOUT-REF-9");
        verify(walletWithdrawalService).confirmPspWithdrawal(OPERATION_ID);
    }

    @Test
    void processWebhook_processingFailure_marksReconciliationNeededAndDoesNotThrow() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.findByPaymentIdOrPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);
        when(operationService.confirmOperation(OPERATION_ID)).thenThrow(new RuntimeException("ledger unavailable"));

        assertThatCode(() -> service.processWebhook(
                new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null, null),
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
    void checkAndApplyPayoutStatus_matchingAmount_appliesStatusAndPersistsMetadata() {
        Operation op = pendingPspOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.findByPayoutIdOrPspReference(PAYOUT_ID)).thenReturn(Optional.of(op));

        JawuPayPayoutDetailsResponse payout = new JawuPayPayoutDetailsResponse(
                UUID.fromString(PAYOUT_ID), null, "SUCCESS",
                150000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", "PAYOUT-REF-9",
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayout(PAYOUT_ID)).thenReturn(payout);
        when(operationService.recordPspMetadata(eq(OPERATION_ID), any())).thenReturn(op);

        Operation confirmed = confirmedOperation(OperationType.WALLET_WITHDRAWAL);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.checkAndApplyPayoutStatus(PAYOUT_ID);

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).recordPspMetadata(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().payoutId()).isEqualTo(PAYOUT_ID);
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("PAYOUT-REF-9");
        assertThat(metadataCaptor.getValue().paymentMethod()).isEqualTo("MOBILE_MONEY_MTN");
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("SUCCESS");
        verify(walletWithdrawalService).confirmPspWithdrawal(OPERATION_ID);
    }

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
}
