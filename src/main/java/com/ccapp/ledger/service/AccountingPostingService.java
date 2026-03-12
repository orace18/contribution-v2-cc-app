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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AccountingPostingService {

    private static final String ACCOUNT_CASH_CLEARING = "1000_CASH_CLEARING";
    private static final String ACCOUNT_LOAN_PORTFOLIO = "1300_LOAN_PORTFOLIO";
    private static final String ACCOUNT_SUBSCRIPTION_REVENUE = "4000_SUBSCRIPTION_REVENUE";
    private static final String ACCOUNT_ADJUSTMENT_EXPENSE = "6100_RECON_ADJUSTMENT_EXPENSE";
    private static final String ACCOUNT_MLM_COMMISSION_EXPENSE = "6200_MLM_COMMISSION_EXPENSE";
    private static final String ACCOUNT_WITHHOLDING_TAX_PAYABLE = "2200_WITHHOLDING_TAX_PAYABLE";
    private static final String MEMBER_WALLET_ACCOUNT_PREFIX = "2101_MEMBER_WALLET_";

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

    @Transactional
    public AccountingEvent postCommissionPayout(Operation operation,
                                                UUID beneficiaryAccountHolderId,
                                                BigDecimal taxAmount,
                                                BigDecimal netAmount) {
        if (operation.getType() != OperationType.COMMISSION_PAYOUT) {
            throw new BusinessException("Operation type must be COMMISSION_PAYOUT");
        }
        if (taxAmount == null || taxAmount.signum() < 0 || netAmount == null || netAmount.signum() < 0) {
            throw new BusinessException("Invalid payout tax/net amounts");
        }
        if (operation.getAmount().compareTo(taxAmount.add(netAmount)) != 0) {
            throw new BusinessException("Payout amount mismatch against tax/net split");
        }

        List<PostingLine> lines = List.of(
                new PostingLine(ACCOUNT_MLM_COMMISSION_EXPENSE, LedgerEntryType.DEBIT, operation.getAmount(), operation.getCurrency(),
                        "Commission payout gross expense"),
                new PostingLine(resolveMemberWalletAccount(beneficiaryAccountHolderId), LedgerEntryType.CREDIT, netAmount, operation.getCurrency(),
                        "Member wallet credit"),
                new PostingLine(ACCOUNT_WITHHOLDING_TAX_PAYABLE, LedgerEntryType.CREDIT, taxAmount, operation.getCurrency(),
                        "Tax withholding on commission payout")
        );
        return accountingEventRepository.findByOperationId(operation.getId())
                .orElseGet(() -> doPost(operation, lines, AccountingEventType.OPERATION_CONFIRMED));
    }

    private AccountingEvent doPost(Operation operation) {
        AccountingEventType eventType = operation.getType() == OperationType.ADJUSTMENT
                ? AccountingEventType.ADJUSTMENT_POSTED
                : AccountingEventType.OPERATION_CONFIRMED;
        List<PostingLine> lines = buildPostingLines(operation);
        return doPost(operation, lines, eventType);
    }

    private AccountingEvent doPost(Operation operation, List<PostingLine> lines, AccountingEventType eventType) {
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED operations can be posted");
        }

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
                .eventType(eventType)
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
        log.info("ledger_posting_completed operationId={} operationType={} eventType={} entries={} totalDebit={} totalCredit={}",
                operation.getId(), operation.getType(), eventType, entries.size(), totalDebit, totalCredit);
        return savedEvent;
    }

    private String resolveMemberWalletAccount(UUID accountHolderId) {
        return MEMBER_WALLET_ACCOUNT_PREFIX + accountHolderId;
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
            case COMMISSION_PAYOUT -> throw new BusinessException(
                    "Use postCommissionPayout for COMMISSION_PAYOUT operations"
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
