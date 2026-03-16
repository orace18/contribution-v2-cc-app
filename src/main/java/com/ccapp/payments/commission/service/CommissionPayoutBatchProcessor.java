package com.ccapp.payments.commission.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.payments.commission.domain.entity.CommissionPayoutBatch;
import com.ccapp.payments.commission.domain.entity.CommissionPayoutItem;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionPayoutBatchStatus;
import com.ccapp.payments.commission.domain.enums.CommissionPayoutItemStatus;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionPayoutBatchRepository;
import com.ccapp.payments.commission.repository.CommissionPayoutItemRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionPayoutBatchProcessor {

    private static final int SCALE = 4;

    private final CommissionPayoutBatchRepository payoutBatchRepository;
    private final CommissionPayoutItemRepository payoutItemRepository;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final WalletRepository walletRepository;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final FinancialAuditService financialAuditService;

    @Value("${commission.payout.tax-rate-percent:10.0}")
    private BigDecimal taxRatePercent;

    @Transactional
    public CommissionPayoutProcessingResult processWeek(int weekId) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        CommissionPayoutBatch batch = resolveOrCreateBatch(weekId);
        if (batch.getStatus() == CommissionPayoutBatchStatus.COMPLETED) {
            return buildResult(batch);
        }

        batch.setStatus(CommissionPayoutBatchStatus.PROCESSING);
        payoutBatchRepository.save(batch);
        log.info("Commission payout batch processing started batchId={} weekId={}", batch.getId(), weekId);

        List<MlmCommission> pendingWeekCommissions = mlmCommissionRepository
                .findByWeekIdAndCommissionTypeAndStatusOrderByBeneficiaryAccountHolderIdAsc(
                        weekId, CommissionType.GROUP_BONUS, CommissionStatus.PENDING);

        int paidItems = 0;
        int failedItems = 0;
        for (MlmCommission commission : pendingWeekCommissions) {
            if (commission.getStatus() != CommissionStatus.PENDING) {
                continue;
            }

            CommissionPayoutItem item = resolveOrCreatePayoutItem(batch.getId(), commission);
            if (item.getStatus() == CommissionPayoutItemStatus.PAID) {
                continue;
            }

            try {
                processSingleItem(batch, item, commission);
                paidItems++;
            } catch (RuntimeException ex) {
                failedItems++;
                item.setStatus(CommissionPayoutItemStatus.FAILED);
                item.setFailureReason(truncateFailureReason(ex.getMessage()));
                payoutItemRepository.save(item);
                log.warn("Commission payout item failed batchId={} itemId={} commissionId={} accountHolderId={}",
                        batch.getId(), item.getId(), commission.getId(), commission.getBeneficiaryAccountHolderId(), ex);
            }
        }

        batch.setStatus(failedItems > 0 ? CommissionPayoutBatchStatus.FAILED : CommissionPayoutBatchStatus.COMPLETED);
        payoutBatchRepository.save(batch);
        Duration duration = Duration.between(startedAt, OffsetDateTime.now());
        PlatformMonitoringHooks.onPayoutBatchProcessed(batch.getStatus().name(), paidItems, failedItems, duration);
        log.info("commission_payout_batch_finished batchId={} weekId={} status={} paidItems={} failedItems={} durationMs={}",
                batch.getId(), weekId, batch.getStatus(), paidItems, failedItems, duration.toMillis());

        return new CommissionPayoutProcessingResult(batch.getId(), weekId, batch.getStatus(), paidItems, failedItems);
    }

    private CommissionPayoutBatch resolveOrCreateBatch(int weekId) {
        return payoutBatchRepository.findByWeekId(weekId)
                .orElseGet(() -> {
                    try {
                        return payoutBatchRepository.save(CommissionPayoutBatch.builder()
                                .weekId(weekId)
                                .status(CommissionPayoutBatchStatus.CREATED)
                                .build());
                    } catch (DataIntegrityViolationException ex) {
                        return payoutBatchRepository.findByWeekId(weekId).orElseThrow(() -> ex);
                    }
                });
    }

    private CommissionPayoutItem resolveOrCreatePayoutItem(UUID batchId, MlmCommission commission) {
        BigDecimal grossAmount = commission.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal taxAmount = grossAmount
                .multiply(taxRatePercent)
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(taxAmount).setScale(SCALE, RoundingMode.HALF_UP);

        CommissionPayoutItem item = payoutItemRepository.findByCommissionId(commission.getId())
                .orElseGet(() -> CommissionPayoutItem.builder()
                        .batchId(batchId)
                        .accountHolderId(commission.getBeneficiaryAccountHolderId())
                        .commissionId(commission.getId())
                        .build());

        if (item.getStatus() == CommissionPayoutItemStatus.PAID) {
            return item;
        }

        item.setBatchId(batchId);
        item.setAccountHolderId(commission.getBeneficiaryAccountHolderId());
        item.setCommissionId(commission.getId());
        item.setGrossAmount(grossAmount);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(netAmount);
        item.setStatus(CommissionPayoutItemStatus.CREATED);
        item.setFailureReason(null);
        item.setProcessedAt(null);

        try {
            return payoutItemRepository.save(item);
        } catch (DataIntegrityViolationException ex) {
            return payoutItemRepository.findByCommissionId(commission.getId())
                    .orElseThrow(() -> ex);
        }
    }

    private void processSingleItem(CommissionPayoutBatch batch,
                                   CommissionPayoutItem item,
                                   MlmCommission commission) {
        verifyPayoutIntegrity(item, commission);
        requireActiveWallet(item.getAccountHolderId());
        String currency = commission.getCurrency();

        Operation payoutOperation = resolveOrCreateConfirmedPayoutOperation(batch, item, commission, currency);
        accountingPostingService.postCommissionPayout(
                payoutOperation,
                item.getAccountHolderId(),
                item.getTaxAmount(),
                item.getNetAmount()
        );

        OffsetDateTime paidAt = OffsetDateTime.now();
        commission.setStatus(CommissionStatus.PAID);
        commission.setPaidAt(paidAt);
        commission.setPayoutBatchId(batch.getId());
        commission.setPayoutItemId(item.getId());
        mlmCommissionRepository.save(commission);

        item.setStatus(CommissionPayoutItemStatus.PAID);
        item.setFailureReason(null);
        item.setProcessedAt(paidAt);
        payoutItemRepository.save(item);
        financialAuditService.record(
                "COMMISSION_PAYOUT",
                item.getId().toString(),
                item.getAccountHolderId(),
                item.getGrossAmount(),
                Map.of(
                        "commissionId", String.valueOf(item.getCommissionId()),
                        "batchId", batch.getId().toString(),
                        "weekId", String.valueOf(batch.getWeekId()),
                        "taxAmount", item.getTaxAmount().toPlainString(),
                        "netAmount", item.getNetAmount().toPlainString(),
                        "operationId", String.valueOf(item.getOperationId())
                )
        );
        financialAuditService.record(
                "WALLET_CREDIT",
                item.getOperationId() != null ? item.getOperationId().toString() : item.getId().toString(),
                item.getAccountHolderId(),
                item.getNetAmount(),
                Map.of(
                        "commissionId", String.valueOf(item.getCommissionId()),
                        "source", "COMMISSION_PAYOUT",
                        "batchId", batch.getId().toString(),
                        "payoutItemId", item.getId().toString(),
                        "weekId", String.valueOf(batch.getWeekId())
                )
        );

        log.info("Commission payout item paid batchId={} itemId={} accountHolderId={} gross={} tax={} net={}",
                batch.getId(),
                item.getId(),
                item.getAccountHolderId(),
                item.getGrossAmount(),
                item.getTaxAmount(),
                item.getNetAmount());
    }

    private void verifyPayoutIntegrity(CommissionPayoutItem item, MlmCommission commission) {
        if (commission.getStatus() != CommissionStatus.PENDING) {
            throw new BusinessException("Commission is not payable");
        }
        if (commission.getPayoutItemId() != null && !commission.getPayoutItemId().equals(item.getId())) {
            throw new BusinessException("Commission already linked to another payout item");
        }
        if (item.getGrossAmount() == null || item.getGrossAmount().compareTo(commission.getAmount()) != 0) {
            throw new BusinessException("Payout gross amount mismatch");
        }
        if (item.getTaxAmount() == null || item.getTaxAmount().signum() < 0) {
            throw new BusinessException("Invalid payout tax amount");
        }
        if (item.getNetAmount() == null || item.getNetAmount().signum() < 0) {
            throw new BusinessException("Invalid payout net amount");
        }
        if (item.getGrossAmount().compareTo(item.getTaxAmount().add(item.getNetAmount())) != 0) {
            throw new BusinessException("Invalid payout split");
        }
    }

    private void requireActiveWallet(UUID accountHolderId) {
        Wallet wallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Active wallet not found for account holder"));
        if (wallet.getId() == null) {
            throw new BusinessException("Invalid wallet state for payout");
        }
    }

    private Operation resolveOrCreateConfirmedPayoutOperation(CommissionPayoutBatch batch,
                                                              CommissionPayoutItem item,
                                                              MlmCommission commission,
                                                              String currency) {
        if (item.getOperationId() != null) {
            Operation existing = operationService.getById(item.getOperationId());
            return existing.getStatus() == OperationStatus.CONFIRMED
                    ? existing
                    : operationService.confirmOperation(existing.getId());
        }

        String idempotencyKey = "commission-payout-item:" + item.getId();
        Operation initiated = operationService.createInitiatedOperation(
                item.getAccountHolderId(),
                item.getAccountHolderId(),
                OperationType.COMMISSION_PAYOUT,
                item.getGrossAmount(),
                currency,
                idempotencyKey,
                "Commission payout week " + batch.getWeekId() + " commission " + commission.getId()
        );
        Operation confirmed = operationService.confirmOperation(initiated.getId());
        item.setOperationId(confirmed.getId());
        payoutItemRepository.save(item);
        return confirmed;
    }

    private CommissionPayoutProcessingResult buildResult(CommissionPayoutBatch batch) {
        List<CommissionPayoutItem> items = payoutItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId());
        int paidItems = (int) items.stream().filter(item -> item.getStatus() == CommissionPayoutItemStatus.PAID).count();
        int failedItems = (int) items.stream().filter(item -> item.getStatus() == CommissionPayoutItemStatus.FAILED).count();
        return new CommissionPayoutProcessingResult(
                batch.getId(),
                batch.getWeekId(),
                batch.getStatus(),
                paidItems,
                failedItems
        );
    }

    private String truncateFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Unexpected payout failure";
        }
        return reason.length() <= 512 ? reason : reason.substring(0, 512);
    }
}
