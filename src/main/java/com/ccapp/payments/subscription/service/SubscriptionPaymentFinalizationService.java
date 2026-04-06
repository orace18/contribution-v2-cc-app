package com.ccapp.payments.subscription.service;

import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentFinalizationService {

    private final OperationService operationService;
    private final MembershipService membershipService;
    private final AccountingPostingService accountingPostingService;
    private final MlmCommissionService mlmCommissionService;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional
    public void finalizeConfirmedOperation(UUID operationId) {
        Operation operation = operationService.getById(operationId);
        if (operation.getType() != OperationType.SUBSCRIPTION) {
            return;
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            log.warn("subscription_finalization_skipped operationId={} status={} reason=operation_not_confirmed",
                    operation.getId(),
                    operation.getStatus());
            return;
        }

        log.info("subscription_finalization_started operationId={} accountHolderId={} planId={} currency={} amount={}",
                operation.getId(),
                operation.getAccountHolderId(),
                operation.getPlanId(),
                operation.getCurrency(),
                operation.getAmount());
        membershipService.activateForConfirmedOperation(operation);
        postAccountingSafely(operation);
        calculateCommissionSafely(operation);
        mobileOnboardingJourneyService.onPaidPaymentConfirmed(operation);
        log.info("subscription_finalization_completed operationId={} accountHolderId={} planId={}",
                operation.getId(),
                operation.getAccountHolderId(),
                operation.getPlanId());
    }

    private void postAccountingSafely(Operation operation) {
        try {
            accountingPostingService.postConfirmedOperation(operation.getId());
        } catch (Exception ex) {
            log.error("subscription_accounting_posting_failed operationId={} accountHolderId={} planId={} retryable=true message={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    ex.getMessage(),
                    ex);
        }
    }

    private void calculateCommissionSafely(Operation operation) {
        try {
            mlmCommissionService.calculateForOperation(operation);
        } catch (Exception ex) {
            log.error("subscription_commission_generation_failed operationId={} accountHolderId={} planId={} retryable=true message={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    ex.getMessage(),
                    ex);
        }
    }
}
