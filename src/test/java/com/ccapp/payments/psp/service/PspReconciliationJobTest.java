package com.ccapp.payments.psp.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.repository.OperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

        when(operationRepository.findBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), eq("RECONCILIATION_REQUIRED"), any(), any()))
                .thenReturn(List.of(payment, payout));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-123");
        verify(jawuPayWebhookService).checkAndApplyPayoutStatus("PAYOUT-456");
    }

    @Test
    void reconcileStuckOperations_shouldSkipWhenIdentifiersMissing() {
        Operation missingIdentifiers = pendingOperation();

        when(operationRepository.findBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), eq("RECONCILIATION_REQUIRED"), any(), any()))
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

        when(operationRepository.findBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), eq("RECONCILIATION_REQUIRED"), any(), any()))
                .thenReturn(List.of(payment, payout));
        doThrow(new RuntimeException("PSP unavailable"))
                .when(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-ERR");

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-ERR");
        verify(jawuPayWebhookService).checkAndApplyPayoutStatus("PAYOUT-OK");
    }

    @Test
    void reconcileStuckOperations_shouldProcessReconciliationRequiredOperations() {
        Operation reconciliationRequired = pendingOperation();
        reconciliationRequired.setStatus(OperationStatus.FAILED);
        reconciliationRequired.setPspStatus("RECONCILIATION_REQUIRED");
        reconciliationRequired.setPaymentId("PAYMENT-RETRY");

        when(operationRepository.findBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), eq("RECONCILIATION_REQUIRED"), any(), any()))
                .thenReturn(List.of(reconciliationRequired));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus("PAYMENT-RETRY");
    }

    @Test
    void reconcileStuckOperations_shouldHonorConfiguredBatchSize() {
        ReflectionTestUtils.setField(pspReconciliationJob, "batchSize", 25);

        when(operationRepository.findBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), eq("RECONCILIATION_REQUIRED"), any(), any()))
                .thenReturn(List.of());

        pspReconciliationJob.reconcileStuckOperations();

        verify(operationRepository).findBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP),
                eq("RECONCILIATION_REQUIRED"),
                any(),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 25));
    }

    private Operation pendingOperation() {
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID());
        operation.setStatus(OperationStatus.PENDING_PSP);
        operation.setPspStatus("PENDING_PSP");
        return operation;
    }
}
