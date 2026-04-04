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
import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
    void reconcileStuckOperations_shouldReconcilePendingPayment() {
        Operation payment = pendingOperation();
        payment.setPaymentId("PAYMENT-123");

        when(operationRepository.findPendingPaymentBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), any(), any()))
                .thenReturn(List.of(payment));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus(payment);
    }

    @Test
    void reconcileStuckOperations_shouldContinueWhenOneCallFails() {
        Operation payment = pendingOperation();
        payment.setPaymentId("PAYMENT-ERR");
        Operation nextPayment = pendingOperation();
        nextPayment.setProviderReference("PAYMENT-OK");

        when(operationRepository.findPendingPaymentBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), any(), any()))
                .thenReturn(List.of(payment, nextPayment));
        doThrow(new RuntimeException("PSP unavailable"))
                .when(jawuPayWebhookService).checkAndApplyPaymentStatus(payment);

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus(payment);
        verify(jawuPayWebhookService).checkAndApplyPaymentStatus(nextPayment);
    }

    @Test
    void reconcileStuckOperations_shouldMarkTimedOutOperationsAsFailed() {
        Operation expiredPayment = pendingOperation();
        expiredPayment.setPaymentId("PAYMENT-OLD");
        expiredPayment.setCreatedAt(OffsetDateTime.now().minusHours(25));

        when(operationRepository.findPendingPaymentBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), any(), any()))
                .thenReturn(List.of(expiredPayment));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).markOperationAsTimedOut(expiredPayment);
        verify(jawuPayWebhookService, never()).checkAndApplyPaymentStatus(any(Operation.class));
    }

    @Test
    void reconcileStuckOperations_shouldSkipTimedOutRuleWhenCreatedAtMissing() {
        Operation payment = pendingOperation();
        payment.setProviderReference("PROVIDER-REF-1");
        payment.setCreatedAt(null);

        when(operationRepository.findPendingPaymentBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), any(), any()))
                .thenReturn(List.of(payment));

        pspReconciliationJob.reconcileStuckOperations();

        verify(jawuPayWebhookService).checkAndApplyPaymentStatus(payment);
        verify(jawuPayWebhookService, never()).markOperationAsTimedOut(any(Operation.class));
    }

    @Test
    void reconcileStuckOperations_shouldHonorConfiguredBatchSize() {
        ReflectionTestUtils.setField(pspReconciliationJob, "batchSize", 25);

        when(operationRepository.findPendingPaymentBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP), any(), any()))
                .thenReturn(List.of());

        pspReconciliationJob.reconcileStuckOperations();

        verify(operationRepository).findPendingPaymentBatchForPspReconciliation(
                eq(OperationStatus.PENDING_PSP),
                any(),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 25));
    }

    private Operation pendingOperation() {
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID());
        operation.setStatus(OperationStatus.PENDING_PSP);
        operation.setPspStatus("PENDING_PSP");
        operation.setCreatedAt(OffsetDateTime.now().minusMinutes(2));
        return operation;
    }
}
