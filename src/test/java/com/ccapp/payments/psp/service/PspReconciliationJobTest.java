package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.repository.OperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PspReconciliationJobTest {

    @Mock
    private OperationRepository operationRepository;
    @Mock
    private JawuPayWebhookService jawuPayWebhookService;

    @InjectMocks
    private PspReconciliationJob pspReconciliationJob;

    @Test
    void reconcileStuckOperations_shouldReconcilePaymentAndPayout() {
        Operation payment = pendingOperation();
        payment.setPaymentId("PAYMENT-123");
        Operation payout = pendingOperation();
        payout.setPayoutId("PAYOUT-456");

        when(operationRepository.findByStatusAndPspStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(OperationStatus.PENDING_PSP), eq("PENDING_PSP"), any(), any()))
                .thenReturn(List.of(payment, payout));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-123");
        verify(jawuPayWebhookService).checkAndApplyPayoutStatus("PAYOUT-456");
    }

    @Test
    void reconcileStuckOperations_shouldSkipWhenIdentifiersMissing() {
        Operation missingIdentifiers = pendingOperation();

        when(operationRepository.findByStatusAndPspStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(OperationStatus.PENDING_PSP), eq("PENDING_PSP"), any(), any()))
                .thenReturn(List.of(missingIdentifiers));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService, never()).checkAndApplyPaymentStatus(anyString());
        verify(jawuPayWebhookService, never()).checkAndApplyPayoutStatus(anyString());
    }

    @Test
    void reconcileStuckOperations_shouldContinueWhenOneCallFails() {
        Operation payment = pendingOperation();
        payment.setPaymentId("PAYMENT-ERR");
        Operation payout = pendingOperation();
        payout.setPayoutId("PAYOUT-OK");

        when(operationRepository.findByStatusAndPspStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(OperationStatus.PENDING_PSP), eq("PENDING_PSP"), any(), any()))
                .thenReturn(List.of(payment, payout));
        doThrow(new RuntimeException("PSP unavailable"))
                .when(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-ERR");

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-ERR");
        verify(jawuPayWebhookService).checkAndApplyPayoutStatus("PAYOUT-OK");
    }

    private Operation pendingOperation() {
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID());
        operation.setStatus(OperationStatus.PENDING_PSP);
        operation.setPspStatus("PENDING_PSP");
        return operation;
    }
}
