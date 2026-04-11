package com.ccapp.payments.subscription.service;

import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.subscription.domain.entity.Membership;
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
        Membership membership = membershipService.activateForConfirmedOperation(operation);
        String paymentMethod = resolvePaymentMethod(operation);
        log.info("subscription_activated operationId={} accountHolderId={} membershipId={} planId={} planCode={} countryCode={} amount={} currency={} paymentMethod={} expiresAt={}",
                operation.getId(),
                operation.getAccountHolderId(),
                membership.getId(),
                operation.getPlanId(),
                membership.getPlanCode(),
                membership.getCountryCode(),
                operation.getAmount(),
                operation.getCurrency(),
                paymentMethod,
                membership.getExpiresAt());
        postAccountingSafely(operation, membership, paymentMethod);
        calculateCommissionSafely(operation, membership, paymentMethod);
        mobileOnboardingJourneyService.onPaidPaymentConfirmed(operation);
        log.info("payment_confirmed_event_applied operationId={} accountHolderId={} planId={} countryCode={} amount={} currency={} paymentMethod={}",
                operation.getId(),
                operation.getAccountHolderId(),
                operation.getPlanId(),
                membership.getCountryCode(),
                operation.getAmount(),
                operation.getCurrency(),
                paymentMethod);
        log.info("subscription_finalization_completed operationId={} accountHolderId={} planId={} countryCode={} paymentMethod={}",
                operation.getId(),
                operation.getAccountHolderId(),
                operation.getPlanId(),
                membership.getCountryCode(),
                paymentMethod);
    }

    private void postAccountingSafely(Operation operation, Membership membership, String paymentMethod) {
        try {
            var accountingEvent = accountingPostingService.postConfirmedOperation(operation.getId());
            log.info("accounting_posting_created operationId={} accountHolderId={} planId={} countryCode={} amount={} currency={} paymentMethod={} accountingEventId={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    membership.getCountryCode(),
                    operation.getAmount(),
                    operation.getCurrency(),
                    paymentMethod,
                    accountingEvent.getId());
        } catch (Exception ex) {
            log.error("subscription_accounting_posting_failed operationId={} accountHolderId={} planId={} retryable=true message={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    ex.getMessage(),
                    ex);
        }
    }

    private void calculateCommissionSafely(Operation operation, Membership membership, String paymentMethod) {
        try {
            boolean commissionCreated = mlmCommissionService.calculateForOperation(operation).isPresent();
            log.info("distribution_applied operationId={} accountHolderId={} planId={} countryCode={} amount={} currency={} paymentMethod={} commissionCreated={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    membership.getCountryCode(),
                    operation.getAmount(),
                    operation.getCurrency(),
                    paymentMethod,
                    commissionCreated);
        } catch (Exception ex) {
            log.error("subscription_commission_generation_failed operationId={} accountHolderId={} planId={} retryable=true message={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    ex.getMessage(),
                    ex);
        }
    }

    private String resolvePaymentMethod(Operation operation) {
        return operation.getPspReference() == null || operation.getPspReference().isBlank()
                ? "WALLET"
                : "PSP";
    }
}
