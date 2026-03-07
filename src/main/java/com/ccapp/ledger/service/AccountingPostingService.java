package com.ccapp.ledger.service;

import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.domain.enums.AccountingEventType;
import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.repository.AccountingEventRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountingPostingService {

    private static final String ACCOUNT_CASH_CLEARING = "1000_CASH_CLEARING";
    private static final String ACCOUNT_LOAN_PORTFOLIO = "1300_LOAN_PORTFOLIO";
    private static final String ACCOUNT_SUBSCRIPTION_REVENUE = "4000_SUBSCRIPTION_REVENUE";
    private static final String ACCOUNT_ADJUSTMENT_EXPENSE = "6100_RECON_ADJUSTMENT_EXPENSE";

    private final OperationService operationService;
    private final AccountingEventRepository accountingEventRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;

    @Transactional
    public AccountingEvent postConfirmedOperation(UUID operationId) {
        return accountingEventRepository.findByOperationId(operationId)
                .orElseGet(() -> doPost(operationService.getById(operationId)));
    }

    @Transactional
    public AccountingEvent postAdjustment(Operation operation) {
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation));
    }

    private AccountingEvent doPost(Operation operation) {
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }

        List<PostingLine> lines = buildPostingLines(operation);
        validateCurrency(lines, operation.getCurrency());
        validateBalanced(lines);

        BigDecimal totalDebit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.DEBIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.CREDIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountingEvent event = AccountingEvent.builder()
                .operation(operation)
                .eventType(operation.getType() == OperationType.ADJUSTMENT ? AccountingEventType.ADJUSTMENT_POSTED : AccountingEventType.OPERATION_CONFIRMED)
                .currency(operation.getCurrency())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .postedAt(OffsetDateTime.now())
                .build();

        AccountingEvent savedEvent;
        try {
            savedEvent = accountingEventRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            return accountingEventRepository.findByOperationId(operation.getId())
                    .orElseThrow(() -> ex);
        }

        List<LedgerEntry> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            PostingLine line = lines.get(i);
            entries.add(LedgerEntry.builder()
                    .event(savedEvent)
                    .lineNo(i + 1)
                    .accountCode(line.accountCode())
                    .entryType(line.entryType())
                    .amount(line.amount())
                    .currency(line.currency())
                    .description(line.description())
                    .build());
        }

        ledgerEntryRepository.saveAll(entries);
        updateSnapshots(entries);
        return savedEvent;
    }

    private List<PostingLine> buildPostingLines(Operation operation) {
        return switch (operation.getType()) {
            case SUBSCRIPTION -> List.of(
                    new PostingLine(ACCOUNT_CASH_CLEARING, LedgerEntryType.DEBIT, operation.getAmount(), operation.getCurrency(), "Cash received"),
                    new PostingLine(ACCOUNT_SUBSCRIPTION_REVENUE, LedgerEntryType.CREDIT, operation.getAmount(), operation.getCurrency(), "Subscription revenue recognition")
            );
            case ADJUSTMENT -> List.of(
                    new PostingLine(ACCOUNT_ADJUSTMENT_EXPENSE, LedgerEntryType.DEBIT, operation.getAmount(), operation.getCurrency(), "Reconciliation adjustment"),
                    new PostingLine(ACCOUNT_CASH_CLEARING, LedgerEntryType.CREDIT, operation.getAmount(), operation.getCurrency(), "Adjustment against cash")
            );
            case LOAN_DISBURSEMENT -> List.of(
                    new PostingLine(ACCOUNT_LOAN_PORTFOLIO, LedgerEntryType.DEBIT, operation.getAmount(), operation.getCurrency(), "Loan principal recognized"),
                    new PostingLine(ACCOUNT_CASH_CLEARING, LedgerEntryType.CREDIT, operation.getAmount(), operation.getCurrency(), "Loan disbursement cash-out")
            );
            case LOAN_REPAYMENT -> List.of(
                    new PostingLine(ACCOUNT_CASH_CLEARING, LedgerEntryType.DEBIT, operation.getAmount(), operation.getCurrency(), "Loan repayment cash-in"),
                    new PostingLine(ACCOUNT_LOAN_PORTFOLIO, LedgerEntryType.CREDIT, operation.getAmount(), operation.getCurrency(), "Loan principal repayment")
            );
        };
    }

    private void validateCurrency(List<PostingLine> lines, String operationCurrency) {
        boolean invalid = lines.stream().anyMatch(line -> !operationCurrency.equals(line.currency()));
        if (invalid) {
            throw new BusinessException("Strict currency verification failed");
        }
    }

    private void validateBalanced(List<PostingLine> lines) {
        BigDecimal debit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.DEBIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream()
                .filter(line -> line.entryType() == LedgerEntryType.CREDIT)
                .map(PostingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debit.compareTo(credit) != 0) {
            throw new BusinessException("Ledger entries are not balanced");
        }
    }

    private void updateSnapshots(List<LedgerEntry> entries) {
        for (LedgerEntry entry : entries) {
            AccountBalanceSnapshot snapshot = snapshotRepository
                    .findByAccountCodeAndCurrency(entry.getAccountCode(), entry.getCurrency())
                    .orElseGet(() -> snapshotRepository.save(AccountBalanceSnapshot.builder()
                            .accountCode(entry.getAccountCode())
                            .currency(entry.getCurrency())
                            .balance(BigDecimal.ZERO)
                            .version(0L)
                            .build()));

            BigDecimal delta = entry.getEntryType() == LedgerEntryType.DEBIT
                    ? entry.getAmount()
                    : entry.getAmount().negate();
            snapshot.setBalance(snapshot.getBalance().add(delta));
            snapshotRepository.save(snapshot);
        }
    }

    private record PostingLine(
            String accountCode,
            LedgerEntryType entryType,
            BigDecimal amount,
            String currency,
            String description
    ) {
    }
}
