package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlmCommissionServiceTest {

    @Mock
    private MlmCommissionRepository mlmCommissionRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private FinancialAuditService financialAuditService;
    @Mock
    private CommissionEventRateLimiter commissionEventRateLimiter;

    @InjectMocks
    private MlmCommissionService mlmCommissionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mlmCommissionService, "commissionRatePercent", new BigDecimal("5.0"));
        ReflectionTestUtils.setField(mlmCommissionService, "commissionSpikeThreshold", new BigDecimal("10000"));
        ReflectionTestUtils.setField(mlmCommissionService, "commissionGenerationMaxPerMinute", 2000);
        when(commissionEventRateLimiter.allow(any(), anyInt())).thenReturn(true);
    }

    @Test
    void calculateForOperation_shouldPersistAndPublish_whenSubscriptionOperation() {
        UUID operationId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(beneficiaryId)
                .type(OperationType.SUBSCRIPTION)
                .amount(new BigDecimal("10000.00"))
                .currency("XOF")
                .build();

        when(mlmCommissionRepository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(mlmCommissionRepository.save(any(MlmCommission.class))).thenAnswer(invocation -> {
            MlmCommission commission = invocation.getArgument(0);
            commission.setId(UUID.randomUUID());
            return commission;
        });

        mlmCommissionService.calculateForOperation(operation);

        ArgumentCaptor<CommissionGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(CommissionGeneratedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        CommissionGeneratedEvent event = eventCaptor.getValue();
        assertEquals(operationId, event.operationId());
        assertEquals(beneficiaryId, event.beneficiaryAccountHolderId());
        assertEquals(new BigDecimal("500.0000"), event.amount());
        assertEquals("XOF", event.currency());
        assertEquals("SUBSCRIPTION", event.source());
    }

    @Test
    void calculateForOperation_shouldBeIdempotent_whenCommissionAlreadyExists() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .amount(new BigDecimal("10000.00"))
                .currency("XOF")
                .build();

        when(mlmCommissionRepository.findByOperationId(operationId))
                .thenReturn(Optional.of(MlmCommission.builder().id(UUID.randomUUID()).operationId(operationId).build()));

        mlmCommissionService.calculateForOperation(operation);

        verify(mlmCommissionRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }
}
