package com.ccapp.accounting.operation.service;

import com.ccapp.accounting.operation.domain.entity.Operation;
import com.ccapp.accounting.operation.domain.enums.OperationStatus;
import com.ccapp.accounting.operation.domain.enums.OperationType;
import com.ccapp.accounting.operation.repository.OperationRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
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

    @InjectMocks
    private OperationService operationService;

    @Test
    void createInitiatedOperation_shouldReturnExisting_whenIdempotencyKeyAlreadyExists() {
        UUID userId = UUID.randomUUID();
        String idem = "idem-key-1";
        Operation existing = Operation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("19.99"))
                .currency("USD")
                .idempotencyKey(idem)
                .build();

        when(operationRepository.findByIdempotencyKey(idem)).thenReturn(Optional.of(existing));

        Operation result = operationService.createInitiatedOperation(
                userId,
                OperationType.SUBSCRIPTION,
                new BigDecimal("19.99"),
                "USD",
                idem,
                "Subscription BASIC");

        assertSame(existing, result);
        verify(operationRepository, never()).save(any(Operation.class));
    }

    @Test
    void confirmByPspReference_shouldConfirmOperation_whenPayloadMatches() {
        String pspRef = "PSP-123";
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
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
                .userId(UUID.randomUUID())
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
                .userId(UUID.randomUUID())
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
}
