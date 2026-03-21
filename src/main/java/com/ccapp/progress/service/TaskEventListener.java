package com.ccapp.progress.service;

import com.ccapp.circlesocial.event.CircleContributionEvent;
import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import com.ccapp.progress.event.CircleJoinedEvent;
import com.ccapp.progress.event.LoanApplicationSubmittedEvent;
import com.ccapp.progress.event.LoanDisbursedEvent;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import com.ccapp.progress.event.RepaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventListener {

    private final TaskEngineService taskEngineService;
    private final IdempotentEventListenerExecutor listenerExecutor;

    @EventListener
    public void on(AccountHolderCreatedEvent event) {
        safely("progress.account-holder-created", event, () -> taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true));
    }

    @EventListener
    public void on(PhoneVerifiedEvent event) {
        safely("progress.phone-verified", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.VERIFY_PHONE, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(CircleJoinedEvent event) {
        safely("progress.circle-joined", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.JOIN_CIRCLE, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(CircleContributionEvent event) {
        safely("progress.contribution-received", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.MAKE_FIRST_CONTRIBUTION, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(LoanApplicationSubmittedEvent event) {
        safely("progress.loan-application-submitted", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.APPLY_LOAN, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(LoanDisbursedEvent event) {
        safely("progress.loan-disbursed", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.APPLY_LOAN, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(RepaymentCompletedEvent event) {
        safely("progress.repayment-completed", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.REPAY_INSTALLMENT, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(LoanRepaidEvent event) {
        safely("progress.loan-repaid", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.REPAY_INSTALLMENT, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    private void safely(String listenerKey, DomainEvent event, Runnable handler) {
        try {
            listenerExecutor.execute(listenerKey, event, handler);
        } catch (RuntimeException ex) {
            log.warn("Task event listener failed listenerKey={} eventId={}", listenerKey, event.eventId(), ex);
        }
    }
}
