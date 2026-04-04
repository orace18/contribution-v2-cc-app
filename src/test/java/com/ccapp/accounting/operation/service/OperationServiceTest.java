package com.ccapp.accounting.operation.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.wallet.service.WalletLedgerProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    @Mock
    private OperationRepository operationRepository;

    @Mock
    private WalletLedgerProjectionService walletLedgerProjectionService;

    @InjectMocks
    private OperationService operationService;

    @Test
    void createInitiatedOperation_shouldReturnExisting_whenIdempotencyKeyAlreadyExists() {
        UUID accountHolderId = UUID.randomUUID();
        String idem = "idem-key-1";
        Operation existing = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("19.99"))
                .currency("USD")
                .idempotencyKey(idem)
                .build();

        when(operationRepository.findByIdempotencyKey(idem)).thenReturn(Optional.of(existing));

        Operation result = operationService.createInitiatedOperation(
                accountHolderId,
                OperationType.SUBSCRIPTION,
                new BigDecimal("19.99"),
                "USD",
                idem,
                "Subscription BASIC");

        assertSame(existing, result);
        verify(operationRepository, never()).save(any(Operation.class));
    }

    @Test
    void createInitiatedOperation_shouldThrowConflict_whenIdempotencyKeyReusedWithDifferentSignature() {
        UUID accountHolderId = UUID.randomUUID();
        String idem = "idem-key-conflict";
        Operation existing = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("19.99"))
                .currency("USD")
                .idempotencyKey(idem)
                .build();

        when(operationRepository.findByIdempotencyKey(idem)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> operationService.createInitiatedOperation(
                accountHolderId,
                accountHolderId,
                OperationType.LOAN_REPAYMENT,
                new BigDecimal("19.99"),
                "USD",
                idem,
                "Loan repayment"
        ));
        verify(operationRepository, never()).save(any(Operation.class));
    }

    @Test
    void confirmByPspReference_shouldConfirmOperation_whenPayloadMatches() {
        String pspRef = "PSP-123";
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("10.00"))
                .currency("XOF")
                .idempotencyKey("idem-1")
                .pspReference(pspRef)
                .build();

        when(operationRepository.findByPspReference(pspRef)).thenReturn(Optional.of(operation));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operation result = operationService.confirmByPspReference(pspRef, new BigDecimal("10.00"), "XOF");

        assertEquals(OperationStatus.CONFIRMED, result.getStatus());
        verify(operationRepository).save(operation);
    }

    @Test
    void confirmByPspReference_shouldThrowBusinessException_whenCurrencyMismatch() {
        String pspRef = "PSP-456";
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .idempotencyKey("idem-2")
                .pspReference(pspRef)
                .build();

        when(operationRepository.findByPspReference(pspRef)).thenReturn(Optional.of(operation));

        assertThrows(BusinessException.class,
                () -> operationService.confirmByPspReference(pspRef, new BigDecimal("10.00"), "XOF"));
        verify(operationRepository, never()).save(any(Operation.class));
    }

    @Test
    void assignPspReference_shouldThrowConflict_whenDifferentReferenceAlreadySet() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .idempotencyKey("idem-3")
                .pspReference("PSP-OLD")
                .build();

        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));

        assertThrows(ConflictException.class, () -> operationService.assignPspReference(operationId, "PSP-NEW"));
        verify(operationRepository, never()).save(any(Operation.class));
    }

    @Test
    void markPendingPsp_shouldPersistStructuredMetadataAndSetPendingStatus() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("15.00"))
                .currency("XOF")
                .idempotencyKey("idem-4")
                .build();

        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operation result = operationService.markPendingPsp(
                operationId,
                OperationService.PspMetadata.payment(
                        "PAY-123",
                        "PROVIDER-456",
                        "https://checkout.example/pay/PAY-123",
                        "MOBILE_MONEY_MTN",
                        "pending",
                        "CI"
                )
        );

        assertEquals(OperationStatus.PENDING_PSP, result.getStatus());
        assertEquals("PAY-123", result.getPspReference());
        assertEquals("PAY-123", result.getPaymentId());
        assertEquals("PROVIDER-456", result.getProviderReference());
        assertEquals("https://checkout.example/pay/PAY-123", result.getRedirectUrl());
        assertEquals("MOBILE_MONEY_MTN", result.getPaymentMethod());
        assertEquals("CI", result.getPspCountry());
        assertEquals("PENDING", result.getPspStatus());
        verify(operationRepository).save(operation);
    }

    @Test
    void recordPspMetadata_shouldBackfillPaymentIdWithoutOverwritingLegacyReference() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("25.00"))
                .currency("XOF")
                .idempotencyKey("idem-5")
                .pspReference("PROVIDER-LEGACY")
                .pspCountry("CI")
                .build();

        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operation result = operationService.recordPspMetadata(
                operationId,
                new OperationService.PspMetadata(
                        "PAY-789",
                        "PAY-789",
                        null,
                        "PROVIDER-LEGACY",
                        null,
                        "CARD",
                        "completed",
                        "{\"status\":\"completed\"}"
                )
        );

        assertEquals("PROVIDER-LEGACY", result.getPspReference());
        assertEquals("PAY-789", result.getPaymentId());
        assertEquals("PROVIDER-LEGACY", result.getProviderReference());
        assertEquals("CARD", result.getPaymentMethod());
        assertEquals("CI", result.getPspCountry());
        assertEquals("COMPLETED", result.getPspStatus());
        assertEquals("{\"status\":\"completed\"}", result.getPspPayload());
        verify(operationRepository).save(operation);
    }

    @Test
    void findByPaymentIdOrPspReference_shouldFallbackToLegacyReference() {
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("5.00"))
                .currency("XOF")
                .idempotencyKey("idem-6")
                .pspReference("PAY-LEGACY")
                .build();

        when(operationRepository.findByPaymentId("PAY-LEGACY")).thenReturn(Optional.empty());
        when(operationRepository.findByPspReference("PAY-LEGACY")).thenReturn(Optional.of(operation));

        Optional<Operation> result = operationService.findByPaymentIdOrPspReference(" PAY-LEGACY ");

        assertTrue(result.isPresent());
        assertSame(operation, result.get());
        verify(operationRepository).findByPaymentId("PAY-LEGACY");
        verify(operationRepository).findByPspReference("PAY-LEGACY");
    }

    @Test
    void recordPspMetadata_shouldAllowPspStatusProgression() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("10.00"))
                .currency("XOF")
                .idempotencyKey("idem-7")
                .paymentId("PAY-123")
                .pspStatus("PENDING")
                .build();

        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operation result = operationService.recordPspMetadata(
                operationId,
                OperationService.PspMetadata.statusOnly("success")
        );

        assertEquals("SUCCESS", result.getPspStatus());
        verify(operationRepository).save(operation);
    }
}
