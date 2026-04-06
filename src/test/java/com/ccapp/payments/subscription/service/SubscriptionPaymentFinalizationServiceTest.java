package com.ccapp.payments.subscription.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentFinalizationServiceTest {

    @Mock
    private OperationService operationService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private AccountingPostingService accountingPostingService;

    @Mock
    private MlmCommissionService mlmCommissionService;

    @Mock
    private MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @InjectMocks
    private SubscriptionPaymentFinalizationService subscriptionPaymentFinalizationService;

    @Test
    void finalizeConfirmedOperation_shouldContinueWhenAccountingAndCommissionFail() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("65000.0000"))
                .currency("XOF")
                .build();

        when(operationService.getById(operationId)).thenReturn(operation);
        doThrow(new BusinessException("Missing subscription allocation percentage for commissionPool"))
                .when(accountingPostingService).postConfirmedOperation(operationId);
        when(mlmCommissionService.calculateForOperation(operation))
                .thenThrow(new BusinessException("commissionPool allocation percentage is required"));

        subscriptionPaymentFinalizationService.finalizeConfirmedOperation(operationId);

        verify(membershipService).activateForConfirmedOperation(operation);
        verify(accountingPostingService).postConfirmedOperation(operationId);
        verify(mlmCommissionService).calculateForOperation(operation);
        verify(mobileOnboardingJourneyService).onPaidPaymentConfirmed(operation);
    }

    @Test
    void finalizeConfirmedOperation_shouldIgnoreNonSubscriptionOperations() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("1000.0000"))
                .currency("XOF")
                .build();

        when(operationService.getById(operationId)).thenReturn(operation);

        subscriptionPaymentFinalizationService.finalizeConfirmedOperation(operationId);

        verifyNoInteractions(membershipService, accountingPostingService, mlmCommissionService, mobileOnboardingJourneyService);
    }
}
