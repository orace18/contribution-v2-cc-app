package com.ccapp.accounting.reconciliation.service;

import com.ccapp.accounting.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.accounting.ledger.service.AccountingPostingService;
import com.ccapp.accounting.operation.domain.entity.Operation;
import com.ccapp.accounting.operation.domain.enums.OperationStatus;
import com.ccapp.accounting.operation.domain.enums.OperationType;
import com.ccapp.accounting.operation.repository.OperationRepository;
import com.ccapp.accounting.operation.service.OperationService;
import com.ccapp.accounting.reconciliation.domain.entity.ReconciliationRecord;
import com.ccapp.accounting.reconciliation.dto.*;
import com.ccapp.accounting.reconciliation.repository.ReconciliationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final OperationRepository operationRepository;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;

    @Transactional
    public ReconciliationImportResponse importBatch(ReconciliationImportRequest request) {
        UUID batchId = UUID.randomUUID();
        int matched = 0;
        int mismatched = 0;
        int adjustments = 0;

        List<ReconciliationResultItem> results = new ArrayList<>();

        for (ReconciliationItemRequest item : request.items()) {
            Operation operation = operationRepository.findByPspReference(item.pspReference()).orElse(null);
            if (operation != null
                    && operation.getCurrency().equals(item.currency())
                    && operation.getAmount().compareTo(item.amount()) == 0
                    && operation.getStatus() == OperationStatus.CONFIRMED) {

                matched++;
                reconciliationRecordRepository.save(ReconciliationRecord.builder()
                        .batchId(batchId)
                        .pspReference(item.pspReference())
                        .amount(item.amount())
                        .currency(item.currency())
                        .matched(true)
                        .adjustmentPosted(false)
                        .notes("Matched")
                        .build());

                results.add(new ReconciliationResultItem(item.pspReference(), true, false, BigDecimal.ZERO, "Matched"));
                continue;
            }

            mismatched++;
            BigDecimal delta = BigDecimal.ZERO;
            boolean adjustmentPosted = false;
            String notes;

            if (operation != null && operation.getCurrency().equals(item.currency())) {
                delta = item.amount().subtract(operation.getAmount()).abs();
                if (delta.compareTo(BigDecimal.ZERO) > 0) {
                    Operation adjustment = operationService.createInitiatedOperation(
                            operation.getUserId(),
                            OperationType.ADJUSTMENT,
                            delta,
                            item.currency(),
                            "recon:" + batchId + ":" + item.pspReference(),
                            "Reconciliation adjustment"
                    );
                    adjustment.setStatus(OperationStatus.CONFIRMED);
                    operationRepository.save(adjustment);
                    accountingPostingService.postAdjustment(adjustment);
                    adjustmentPosted = true;
                    adjustments++;
                    notes = "Mismatch with adjustment posted";
                } else {
                    notes = "Mismatch without adjustment";
                }
            } else {
                notes = "Operation not found or currency mismatch";
            }

            reconciliationRecordRepository.save(ReconciliationRecord.builder()
                    .batchId(batchId)
                    .pspReference(item.pspReference())
                    .amount(item.amount())
                    .currency(item.currency())
                    .matched(false)
                    .adjustmentPosted(adjustmentPosted)
                    .notes(notes)
                    .build());

            results.add(new ReconciliationResultItem(item.pspReference(), false, adjustmentPosted, delta, notes));
        }

        return new ReconciliationImportResponse(batchId, request.items().size(), matched, mismatched, adjustments, results);
    }

    @Transactional(readOnly = true)
    public List<SettlementSummaryItem> settlementSummary(String currency) {
        return snapshotRepository.findByCurrencyOrderByAccountCodeAsc(currency).stream()
                .map(snapshot -> new SettlementSummaryItem(snapshot.getAccountCode(), snapshot.getCurrency(), snapshot.getBalance()))
                .toList();
    }
}
