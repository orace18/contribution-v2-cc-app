package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayConfirmResponse;
import com.ccapp.payments.psp.dto.JawuPayQuoteRequest;
import com.ccapp.payments.psp.dto.JawuPayQuoteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JawuPayPaymentOrchestrationServiceTest {

    @Mock
    OperationService operationService;
    @Mock
    JawuPayPspClient pspClient;

    @InjectMocks
    JawuPayPaymentOrchestrationService service;

    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();
    private static final UUID ACTOR_USER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Test
    void initiatePayment_happyPath_returnsRedirectUrlAndMarksPendingPsp() {
        BigDecimal amount = new BigDecimal("1500.00"); // 150000 minor units

        Operation initiated = operationWithStatus(OperationStatus.INITIATED);
        when(operationService.createInitiatedOperation(
                eq(ACCOUNT_HOLDER_ID), eq(ACTOR_USER_ID),
                eq(OperationType.WALLET_DEPOSIT_PSP),
                eq(amount), eq("XOF"), eq("idem-1"), eq("desc")))
                .thenReturn(initiated);

        JawuPayQuoteResponse quote = new JawuPayQuoteResponse(
                QUOTE_ID, 150000L, 0L, 150000L, OffsetDateTime.now().plusMinutes(5));
        when(pspClient.createQuote(any())).thenReturn(quote);

        JawuPayConfirmResponse confirm = new JawuPayConfirmResponse(
                PAYMENT_ID, "PENDING", "MOCK_PSP",
                "https://checkout.example/pay/" + PAYMENT_ID, null, "REF-1");
        when(pspClient.confirmPayment(any(), eq("idem-1"))).thenReturn(confirm);

        JawuPayPaymentOrchestrationService.InitiatePaymentResult result =
                service.initiatePayment(ACCOUNT_HOLDER_ID, ACTOR_USER_ID,
                        amount, "XOF", "CI",
                        "MOBILE_MONEY_MTN", "22501234567", "MTN",
                        "idem-1", "desc");

        assertThat(result.operationId()).isEqualTo(OPERATION_ID);
        assertThat(result.redirectUrl()).isEqualTo("https://checkout.example/pay/" + PAYMENT_ID);

        // Verify quote request uses minor units and correct clientId
        ArgumentCaptor<JawuPayQuoteRequest> quoteCaptor = ArgumentCaptor.forClass(JawuPayQuoteRequest.class);
        verify(pspClient).createQuote(quoteCaptor.capture());
        assertThat(quoteCaptor.getValue().amount()).isEqualTo(150000L);
        assertThat(quoteCaptor.getValue().clientId()).isEqualTo(ACCOUNT_HOLDER_ID.toString());
        assertThat(quoteCaptor.getValue().country()).isEqualTo("CI");

        // Verify confirm request uses quoteId and PayerInfo
        ArgumentCaptor<JawuPayConfirmRequest> confirmCaptor = ArgumentCaptor.forClass(JawuPayConfirmRequest.class);
        verify(pspClient).confirmPayment(confirmCaptor.capture(), eq("idem-1"));
        assertThat(confirmCaptor.getValue().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(confirmCaptor.getValue().payerInfo().msisdn()).isEqualTo("+22501234567");
        assertThat(confirmCaptor.getValue().payerInfo().operatorHint()).isEqualTo("MTN");

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).markPendingPsp(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().pspReference()).isEqualTo(PAYMENT_ID.toString());
        assertThat(metadataCaptor.getValue().paymentId()).isEqualTo(PAYMENT_ID.toString());
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("REF-1");
        assertThat(metadataCaptor.getValue().redirectUrl())
                .isEqualTo("https://checkout.example/pay/" + PAYMENT_ID);
        assertThat(metadataCaptor.getValue().paymentMethod()).isEqualTo("MOBILE_MONEY_MTN");
        assertThat(metadataCaptor.getValue().pspCountry()).isEqualTo("CI");
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("PENDING_PSP");
    }

    @Test
    void initiatePayment_withExplicitOperationType_usesProvidedOperationType() {
        BigDecimal amount = new BigDecimal("1500.00");
        Operation initiated = operationWithStatus(OperationStatus.INITIATED);
        when(operationService.createInitiatedOperation(
                eq(ACCOUNT_HOLDER_ID), eq(ACTOR_USER_ID),
                eq(OperationType.SUBSCRIPTION),
                eq(amount), eq("XOF"), eq("idem-sub"), eq("desc-sub")))
                .thenReturn(initiated);

        JawuPayQuoteResponse quote = new JawuPayQuoteResponse(
                QUOTE_ID, 150000L, 0L, 150000L, OffsetDateTime.now().plusMinutes(5));
        when(pspClient.createQuote(any())).thenReturn(quote);

        JawuPayConfirmResponse confirm = new JawuPayConfirmResponse(
                PAYMENT_ID, "PENDING", "MOCK_PSP",
                "https://checkout.example/pay/" + PAYMENT_ID, null, "REF-SUB-1");
        when(pspClient.confirmPayment(any(), eq("idem-sub"))).thenReturn(confirm);

        JawuPayPaymentOrchestrationService.InitiatePaymentResult result =
                service.initiatePayment(
                        ACCOUNT_HOLDER_ID, ACTOR_USER_ID,
                        amount, "XOF", "BJ",
                        "MOBILE_MONEY_MTN", "22961000000", "MTN",
                        "idem-sub", "desc-sub", OperationType.SUBSCRIPTION
                );

        assertThat(result.operationId()).isEqualTo(OPERATION_ID);
        verify(operationService).createInitiatedOperation(
                ACCOUNT_HOLDER_ID, ACTOR_USER_ID, OperationType.SUBSCRIPTION,
                amount, "XOF", "idem-sub", "desc-sub"
        );
    }

    @Test
    void initiatePayment_withPlanId_persistsPlanOnOperationCreation() {
        BigDecimal amount = new BigDecimal("1500.00");
        UUID planId = UUID.randomUUID();
        Operation initiated = operationWithStatus(OperationStatus.INITIATED);
        when(operationService.createInitiatedOperation(
                eq(ACCOUNT_HOLDER_ID), eq(ACTOR_USER_ID),
                eq(OperationType.SUBSCRIPTION),
                eq(amount), eq("XOF"), eq("idem-plan"), eq("desc-plan"), eq(planId)))
                .thenReturn(initiated);

        JawuPayQuoteResponse quote = new JawuPayQuoteResponse(
                QUOTE_ID, 150000L, 0L, 150000L, OffsetDateTime.now().plusMinutes(5));
        when(pspClient.createQuote(any())).thenReturn(quote);

        JawuPayConfirmResponse confirm = new JawuPayConfirmResponse(
                PAYMENT_ID, "PENDING", "MOCK_PSP",
                "https://checkout.example/pay/" + PAYMENT_ID, null, "REF-SUB-PLAN");
        when(pspClient.confirmPayment(any(), eq("idem-plan"))).thenReturn(confirm);

        service.initiatePayment(
                ACCOUNT_HOLDER_ID, ACTOR_USER_ID,
                amount, "XOF", "BJ",
                "MOBILE_MONEY_MTN", "22961000000", "MTN",
                "idem-plan", "desc-plan", OperationType.SUBSCRIPTION, planId
        );

        verify(operationService).createInitiatedOperation(
                ACCOUNT_HOLDER_ID, ACTOR_USER_ID, OperationType.SUBSCRIPTION,
                amount, "XOF", "idem-plan", "desc-plan", planId
        );
    }

    @Test
    void initiatePayment_withE164Msisdn_keepsExistingPlusPrefix() {
        BigDecimal amount = new BigDecimal("1500.00");

        Operation initiated = operationWithStatus(OperationStatus.INITIATED);
        when(operationService.createInitiatedOperation(
                eq(ACCOUNT_HOLDER_ID), eq(ACTOR_USER_ID),
                eq(OperationType.WALLET_DEPOSIT_PSP),
                eq(amount), eq("XOF"), eq("idem-e164"), eq("desc-e164")))
                .thenReturn(initiated);

        JawuPayQuoteResponse quote = new JawuPayQuoteResponse(
                QUOTE_ID, 150000L, 0L, 150000L, OffsetDateTime.now().plusMinutes(5));
        when(pspClient.createQuote(any())).thenReturn(quote);

        JawuPayConfirmResponse confirm = new JawuPayConfirmResponse(
                PAYMENT_ID, "PENDING", "MOCK_PSP",
                "https://checkout.example/pay/" + PAYMENT_ID, null, "REF-E164");
        when(pspClient.confirmPayment(any(), eq("idem-e164"))).thenReturn(confirm);

        service.initiatePayment(
                ACCOUNT_HOLDER_ID, ACTOR_USER_ID,
                amount, "XOF", "BJ",
                "MOBILE_MONEY_MTN", "+22966000001", "MTN",
                "idem-e164", "desc-e164"
        );

        ArgumentCaptor<JawuPayConfirmRequest> confirmCaptor = ArgumentCaptor.forClass(JawuPayConfirmRequest.class);
        verify(pspClient).confirmPayment(confirmCaptor.capture(), eq("idem-e164"));
        assertThat(confirmCaptor.getValue().payerInfo().msisdn()).isEqualTo("+22966000001");
    }

    @Test
    void initiatePayment_idempotentOperation_skipsPspCalls() {
        Operation alreadyPending = operationWithStatus(OperationStatus.PENDING_PSP);
        alreadyPending.setRedirectUrl("https://checkout.example/pay/existing");
        when(operationService.createInitiatedOperation(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(alreadyPending);

        JawuPayPaymentOrchestrationService.InitiatePaymentResult result =
                service.initiatePayment(ACCOUNT_HOLDER_ID, ACTOR_USER_ID,
                        new BigDecimal("1500.00"), "XOF", "CI",
                        "MOBILE_MONEY_MTN", "22501234567", "MTN",
                        "idem-dup", "desc");

        assertThat(result.operationId()).isEqualTo(OPERATION_ID);
        assertThat(result.redirectUrl()).isEqualTo("https://checkout.example/pay/existing");
        verifyNoMoreInteractions(pspClient);
    }

    @Test
    void initiatePayment_whenConfirmPaymentIdIsNull_persistsQuoteIdAsPaymentId() {
        BigDecimal amount = new BigDecimal("1500.00");

        Operation initiated = operationWithStatus(OperationStatus.INITIATED);
        when(operationService.createInitiatedOperation(
                eq(ACCOUNT_HOLDER_ID), eq(ACTOR_USER_ID),
                eq(OperationType.WALLET_DEPOSIT_PSP),
                eq(amount), eq("XOF"), eq("idem-null"), eq("desc")))
                .thenReturn(initiated);

        JawuPayQuoteResponse quote = new JawuPayQuoteResponse(
                QUOTE_ID, 150000L, 0L, 150000L, OffsetDateTime.now().plusMinutes(5));
        when(pspClient.createQuote(any())).thenReturn(quote);

        JawuPayConfirmResponse confirm = new JawuPayConfirmResponse(
                null, "PENDING", "MOCK_PSP",
                "https://checkout.example/pay/link", null, "REF-NUL");
        when(pspClient.confirmPayment(any(), eq("idem-null"))).thenReturn(confirm);

        service.initiatePayment(ACCOUNT_HOLDER_ID, ACTOR_USER_ID, amount, "XOF", "CI",
                "MOBILE_MONEY_MTN", "22501234567", "MTN", "idem-null", "desc");

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor = ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).markPendingPsp(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().paymentId()).isEqualTo(QUOTE_ID.toString());
        assertThat(metadataCaptor.getValue().pspReference()).isEqualTo(QUOTE_ID.toString());
    }

    @Test
    void toMinorUnits_convertsCorrectly() {
        assertThat(JawuPayPaymentOrchestrationService.toMinorUnits(new BigDecimal("1500.00"))).isEqualTo(150000L);
        assertThat(JawuPayPaymentOrchestrationService.toMinorUnits(new BigDecimal("0.50"))).isEqualTo(50L);
        assertThat(JawuPayPaymentOrchestrationService.toMinorUnits(new BigDecimal("10000"))).isEqualTo(1000000L);
    }

    private Operation operationWithStatus(OperationStatus status) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setStatus(status);
        op.setType(OperationType.WALLET_DEPOSIT_PSP);
        op.setAmount(new BigDecimal("1500.00"));
        op.setCurrency("XOF");
        return op;
    }
}
