package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JawuPayPayoutOrchestrationServiceTest {

    @Mock
    OperationService operationService;
    @Mock
    JawuPayPspClient pspClient;

    @InjectMocks
    JawuPayPayoutOrchestrationService service;

    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();
    private static final UUID ACTOR_USER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final UUID PAYOUT_ID = UUID.randomUUID();

    @Test
    void initiatePayout_happyPath_persistsStructuredPspMetadata() {
        BigDecimal amount = new BigDecimal("2500.00");
        Operation initiated = operationWithStatus(OperationStatus.PAYOUT_INITIATED);
        when(operationService.createPayoutInitiatedOperation(
                eq(ACCOUNT_HOLDER_ID), eq(ACTOR_USER_ID),
                eq(OperationType.WALLET_WITHDRAWAL),
                eq(amount), eq("XOF"), eq("idem-payout"), eq("desc"), any()))
                .thenReturn(initiated);

        JawuPayCreatePayoutResponse payout = new JawuPayCreatePayoutResponse(
                PAYOUT_ID, "PROCESSING", "MOCK_PSP", "PAYOUT-REF-1");
        when(pspClient.createPayout(any())).thenReturn(payout);

        JawuPayPayoutOrchestrationService.InitiatePayoutResult result =
                service.initiatePayout(
                        ACCOUNT_HOLDER_ID,
                        ACTOR_USER_ID,
                        amount,
                        "XOF",
                        "CI",
                        "MOBILE_MONEY_MTN",
                        "22501234567",
                        "MTN",
                        "idem-payout",
                        "desc"
                );

        assertThat(result.operationId()).isEqualTo(OPERATION_ID);
        assertThat(result.payoutId()).isEqualTo(PAYOUT_ID);

        ArgumentCaptor<JawuPayCreatePayoutRequest> requestCaptor =
                ArgumentCaptor.forClass(JawuPayCreatePayoutRequest.class);
        verify(pspClient).createPayout(requestCaptor.capture());
        assertThat(requestCaptor.getValue().amount()).isEqualTo(250000L);
        assertThat(requestCaptor.getValue().clientId()).isEqualTo(ACCOUNT_HOLDER_ID.toString());
        assertThat(requestCaptor.getValue().beneficiaryRef()).isEqualTo("22501234567");

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).markPendingPsp(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().pspReference()).isEqualTo(PAYOUT_ID.toString());
        assertThat(metadataCaptor.getValue().payoutId()).isEqualTo(PAYOUT_ID.toString());
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("PAYOUT-REF-1");
        assertThat(metadataCaptor.getValue().paymentMethod()).isEqualTo("MOBILE_MONEY_MTN");
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("PENDING_PSP");
    }

    @Test
    void createPayout_existingInitiatedOperation_submitsPspPayout() {
        Operation initiated = operationWithStatus(OperationStatus.INITIATED);
        when(operationService.getById(OPERATION_ID)).thenReturn(initiated);

        JawuPayCreatePayoutResponse payout = new JawuPayCreatePayoutResponse(
                PAYOUT_ID, "PROCESSING", "MOCK_PSP", "PAYOUT-REF-2");
        when(pspClient.createPayout(any())).thenReturn(payout);

        JawuPayPayoutOrchestrationService.InitiatePayoutResult result =
                service.createPayout(
                        OPERATION_ID,
                        "CI",
                        "MOBILE_MONEY_MTN",
                        "22501234567",
                        "MTN"
                );

        assertThat(result.operationId()).isEqualTo(OPERATION_ID);
        assertThat(result.payoutId()).isEqualTo(PAYOUT_ID);

        ArgumentCaptor<JawuPayCreatePayoutRequest> requestCaptor =
                ArgumentCaptor.forClass(JawuPayCreatePayoutRequest.class);
        verify(pspClient).createPayout(requestCaptor.capture());
        assertThat(requestCaptor.getValue().amount()).isEqualTo(250000L);
        assertThat(requestCaptor.getValue().clientId()).isEqualTo(ACCOUNT_HOLDER_ID.toString());
        assertThat(requestCaptor.getValue().beneficiaryRef()).isEqualTo("22501234567");

        ArgumentCaptor<OperationService.PspMetadata> metadataCaptor =
                ArgumentCaptor.forClass(OperationService.PspMetadata.class);
        verify(operationService).markPendingPsp(eq(OPERATION_ID), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().payoutId()).isEqualTo(PAYOUT_ID.toString());
        assertThat(metadataCaptor.getValue().providerReference()).isEqualTo("PAYOUT-REF-2");
        assertThat(metadataCaptor.getValue().paymentMethod()).isEqualTo("MOBILE_MONEY_MTN");
        assertThat(metadataCaptor.getValue().pspStatus()).isEqualTo("PENDING_PSP");
    }

    @Test
    void initiatePayout_idempotentOperation_returnsExistingStructuredPayoutId() {
        Operation alreadyPending = operationWithStatus(OperationStatus.PENDING_PSP);
        alreadyPending.setPayoutId(PAYOUT_ID.toString());
        when(operationService.createPayoutInitiatedOperation(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(alreadyPending);

        JawuPayPayoutOrchestrationService.InitiatePayoutResult result =
                service.initiatePayout(
                        ACCOUNT_HOLDER_ID,
                        ACTOR_USER_ID,
                        new BigDecimal("2500.00"),
                        "XOF",
                        "CI",
                        "MOBILE_MONEY_MTN",
                        "22501234567",
                        "MTN",
                        "idem-payout-dup",
                        "desc"
                );

        assertThat(result.operationId()).isEqualTo(OPERATION_ID);
        assertThat(result.payoutId()).isEqualTo(PAYOUT_ID);
        verifyNoMoreInteractions(pspClient);
    }

    private Operation operationWithStatus(OperationStatus status) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setAccountHolderId(ACCOUNT_HOLDER_ID);
        op.setActorUserId(ACTOR_USER_ID);
        op.setStatus(status);
        op.setType(OperationType.WALLET_WITHDRAWAL);
        op.setAmount(new BigDecimal("2500.00"));
        op.setCurrency("XOF");
        return op;
    }
}
