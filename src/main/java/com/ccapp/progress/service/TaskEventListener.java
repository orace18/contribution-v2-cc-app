package com.ccapp.progress.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.credit.event.LoanRepaidEvent;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import com.ccapp.progress.event.CircleJoinedEvent;
import com.ccapp.progress.event.FriendInvitedEvent;
import com.ccapp.progress.event.KycVerifiedEvent;
import com.ccapp.progress.event.LoanApplicationSubmittedEvent;
import com.ccapp.progress.event.LoanDisbursedEvent;
import com.ccapp.progress.event.PhoneVerifiedEvent;
import com.ccapp.progress.event.RepaymentCompletedEvent;
import com.ccapp.wallet.event.WalletCreditedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventListener {

    private final TaskEngineService taskEngineService;
    private final IdempotentEventListenerExecutor listenerExecutor;
    private final OperationRepository operationRepository;

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
    public void on(KycVerifiedEvent event) {
        safely("progress.kyc-verified", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.COMPLETE_KYC, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(WalletCreditedEvent event) {
        safely("progress.wallet-funded", event, () -> {
            if (!isWalletFundingOperation(event.operationId())) {
                return;
            }
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.FUND_WALLET, false);
            taskEngineService.synchronizeTaskGraph(event.accountHolderId(), true);
        });
    }

    @EventListener
    public void on(FriendInvitedEvent event) {
        safely("progress.friend-invited", event, () -> {
            taskEngineService.completeActiveTaskByType(event.accountHolderId(), TaskType.INVITE_FRIENDS, false);
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

    private boolean isWalletFundingOperation(UUID operationId) {
        if (operationId == null) {
            return false;
        }
        Operation operation = operationRepository.findById(operationId).orElse(null);
        if (operation == null || operation.getStatus() != OperationStatus.CONFIRMED) {
            return false;
        }
        return operation.getType() == OperationType.WALLET_DEPOSIT_PSP
                || operation.getType() == OperationType.WALLET_DEPOSIT_AGENT;
    }
}
