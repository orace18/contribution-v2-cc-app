package com.ccapp.agent.service;

import com.ccapp.agent.domain.entity.OfflineTransaction;
import com.ccapp.agent.dto.CreateOfflineTransactionRequest;
import com.ccapp.agent.dto.OfflineTransactionResponse;
import com.ccapp.agent.dto.SyncOfflineTransactionsRequest;
import com.ccapp.agent.dto.SyncOfflineTransactionsResponse;
import com.ccapp.agent.repository.OfflineTransactionRepository;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OfflineTransactionService {

    private final OfflineTransactionRepository offlineTransactionRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AgentService agentService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final NotificationService notificationService;

    @Transactional
    public OfflineTransactionResponse createOfflineTransaction(UUID actorUserId, CreateOfflineTransactionRequest request) {
        agentService.assertActiveAgent(actorUserId);

        if (!accountHolderRepository.existsById(request.accountHolderId())) {
            throw new NotFoundException("Account holder not found");
        }

        OfflineTransaction saved = offlineTransactionRepository.save(OfflineTransaction.builder()
                .agentUserId(actorUserId)
                .accountHolderId(request.accountHolderId())
                .transactionType(request.transactionType())
                .amount(request.amount())
                .currency(request.currency().toUpperCase(Locale.ROOT))
                .status("PENDING_SYNC")
                .build());
        emitAgentActionNotification(saved);

        return toResponse(saved);
    }

    @Transactional
    public SyncOfflineTransactionsResponse sync(UUID actorUserId, SyncOfflineTransactionsRequest request) {
        agentService.assertActiveAgent(actorUserId);

        List<OfflineTransaction> candidates = resolveTransactionsToSync(actorUserId, request);
        int failed = 0;

        for (OfflineTransaction tx : candidates) {
            if (!"PENDING_SYNC".equals(tx.getStatus())) {
                continue;
            }

            try {
                postLedgerIfNeeded(tx);
                tx.setStatus("SYNCED");
                tx.setSyncedAt(OffsetDateTime.now());
            } catch (RuntimeException ex) {
                tx.setStatus("FAILED");
                failed++;
            }
        }

        List<OfflineTransaction> saved = offlineTransactionRepository.saveAll(candidates);
        int synced = (int) saved.stream().filter(tx -> "SYNCED".equals(tx.getStatus())).count();

        return new SyncOfflineTransactionsResponse(
                synced,
                failed,
                saved.stream().map(this::toResponse).toList()
        );
    }

    private List<OfflineTransaction> resolveTransactionsToSync(UUID actorUserId, SyncOfflineTransactionsRequest request) {
        if (request == null || request.transactionIds() == null || request.transactionIds().isEmpty()) {
            return offlineTransactionRepository.findByAgentUserIdAndStatusOrderByCreatedAtAsc(actorUserId, "PENDING_SYNC");
        }
        return offlineTransactionRepository.findByAgentUserIdAndIdInOrderByCreatedAtAsc(actorUserId, request.transactionIds());
    }

    private void postLedgerIfNeeded(OfflineTransaction tx) {
        switch (tx.getTransactionType()) {
            case LOAN_DISBURSEMENT -> postLoanOperation(tx, OperationType.LOAN_DISBURSEMENT, "Offline loan disbursement");
            case LOAN_REPAYMENT -> postLoanOperation(tx, OperationType.LOAN_REPAYMENT, "Offline loan repayment");
            case CONTRIBUTION -> {
                // Contribution is only synchronized as an offline record for now.
            }
        }
    }

    private void postLoanOperation(OfflineTransaction tx, OperationType type, String description) {
        Operation operation = operationService.createInitiatedOperation(
                tx.getAccountHolderId(),
                tx.getAgentUserId(),
                type,
                tx.getAmount(),
                tx.getCurrency(),
                "offline:" + type.name().toLowerCase(Locale.ROOT) + ":" + tx.getId(),
                description
        );
        operationService.confirmOperation(operation.getId());
        accountingPostingService.postConfirmedOperation(operation.getId());
    }

    private OfflineTransactionResponse toResponse(OfflineTransaction tx) {
        return new OfflineTransactionResponse(
                tx.getId(),
                tx.getAgentUserId(),
                tx.getAccountHolderId(),
                tx.getTransactionType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getSyncedAt(),
                tx.getCreatedAt()
        );
    }

    private void emitAgentActionNotification(OfflineTransaction tx) {
        try {
            notificationService.createNotification(new CreateNotificationRequest(
                    tx.getAccountHolderId(),
                    tx.getAgentUserId(),
                    NotificationType.AGENT_ACTION,
                    NotificationChannel.IN_APP,
                    "AGENT_ACTION_OFFLINE_TRANSACTION",
                    "An offline transaction has been recorded by your agent.",
                    null,
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to emit AGENT_ACTION notification for offlineTransactionId={}", tx.getId(), ex);
        }
    }
}
