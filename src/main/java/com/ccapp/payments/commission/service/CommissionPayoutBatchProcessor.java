package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mlm.event.BonusPaidEvent;
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
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.wallet.event.WalletCreditedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionPayoutBatchProcessor {

    private static final int SCALE = 4;

    private final CommissionPayoutBatchRepository payoutBatchRepository;
    private final CommissionPayoutItemRepository payoutItemRepository;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final WalletRepository walletRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final FinancialAuditService financialAuditService;
    private final CountryConfigResolver countryConfigResolver;
    private final DomainEventPublisher domainEventPublisher;

    @Value("${commission.payout.tax-rate-percent:10.0}")
    private BigDecimal taxRatePercent;

    @Value("${commission.payout.subscription.cron:0 59 23 * * SUN}")
    private String subscriptionPayoutCron;

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

    @Scheduled(cron = "${commission.payout.subscription.cron:0 59 23 * * SUN}", zone = "${mlm.group-bonus.zone:Africa/Porto-Novo}")
    @Transactional
    public CommissionPayoutProcessingResult processPendingSubscriptionCommissions() {
        int weekId = currentWeekId();
        OffsetDateTime startedAt = OffsetDateTime.now();
        CommissionPayoutBatch batch = resolveOrCreateBatch(weekId);
        batch.setStatus(CommissionPayoutBatchStatus.PROCESSING);
        payoutBatchRepository.save(batch);

        List<MlmCommission> pendingCommissions = mlmCommissionRepository
                .findByCommissionTypeAndStatusOrderByBeneficiaryAccountHolderIdAscCreatedAtAsc(
                        CommissionType.SUBSCRIPTION,
                        CommissionStatus.PENDING
                );
        if (pendingCommissions.isEmpty()) {
            batch.setStatus(CommissionPayoutBatchStatus.COMPLETED);
            payoutBatchRepository.save(batch);
            return new CommissionPayoutProcessingResult(batch.getId(), weekId, batch.getStatus(), 0, 0);
        }

        int paidItems = 0;
        int failedItems = 0;
        Map<UUID, List<MlmCommission>> commissionsByAccountHolder = pendingCommissions.stream()
                .collect(Collectors.groupingBy(MlmCommission::getBeneficiaryAccountHolderId));

        for (Map.Entry<UUID, List<MlmCommission>> entry : commissionsByAccountHolder.entrySet()) {
            UUID accountHolderId = entry.getKey();
            List<MlmCommission> commissions = entry.getValue();
            try {
                paidItems += processSubscriptionCommissionGroup(batch, accountHolderId, commissions);
            } catch (RuntimeException ex) {
                failedItems += commissions.size();
                log.warn("Subscription commission payout group failed batchId={} accountHolderId={} count={}",
                        batch.getId(), accountHolderId, commissions.size(), ex);
            }
        }

        batch.setStatus(failedItems > 0 ? CommissionPayoutBatchStatus.FAILED : CommissionPayoutBatchStatus.COMPLETED);
        payoutBatchRepository.save(batch);
        Duration duration = Duration.between(startedAt, OffsetDateTime.now());
        PlatformMonitoringHooks.onPayoutBatchProcessed(batch.getStatus().name(), paidItems, failedItems, duration);
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

    private int processSubscriptionCommissionGroup(CommissionPayoutBatch batch,
                                                   UUID accountHolderId,
                                                   List<MlmCommission> commissions) {
        Wallet beneficiaryWallet = requireActiveWallet(accountHolderId);
        BigDecimal grossAmount = commissions.stream()
                .map(MlmCommission::getAmount)
                .reduce(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal taxRate = resolveTaxRate(accountHolderId);
        BigDecimal taxAmount = grossAmount
                .multiply(taxRate)
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(taxAmount).setScale(SCALE, RoundingMode.HALF_UP);
        String currency = commissions.getFirst().getCurrency();

        Operation payoutOperation = resolveOrCreateGroupedPayoutOperation(
                batch.getWeekId(),
                accountHolderId,
                grossAmount,
                currency
        );
        accountingPostingService.postCommissionPayout(payoutOperation, accountHolderId, taxAmount, netAmount);
        domainEventPublisher.publish(WalletCreditedEvent.create(
                beneficiaryWallet.getId(),
                accountHolderId,
                netAmount,
                currency,
                payoutOperation.getId()
        ));

        List<AmountSplit> splits = splitAcrossCommissions(commissions, taxAmount);
        OffsetDateTime paidAt = OffsetDateTime.now();
        for (int i = 0; i < commissions.size(); i++) {
            MlmCommission commission = commissions.get(i);
            AmountSplit split = splits.get(i);
            CommissionPayoutItem item = resolveOrCreatePayoutItem(batch.getId(), commission);
            item.setGrossAmount(split.grossAmount());
            item.setTaxAmount(split.taxAmount());
            item.setNetAmount(split.netAmount());
            item.setOperationId(payoutOperation.getId());
            item.setStatus(CommissionPayoutItemStatus.PAID);
            item.setFailureReason(null);
            item.setProcessedAt(paidAt);
            payoutItemRepository.save(item);

            commission.setStatus(CommissionStatus.PAID);
            commission.setPaidAt(paidAt);
            commission.setPayoutBatchId(batch.getId());
            commission.setPayoutItemId(item.getId());
            mlmCommissionRepository.save(commission);
        }

        financialAuditService.record(
                "COMMISSION_PAYOUT",
                payoutOperation.getId().toString(),
                accountHolderId,
                grossAmount,
                Map.of(
                        "batchId", batch.getId().toString(),
                        "weekId", String.valueOf(batch.getWeekId()),
                        "taxAmount", taxAmount.toPlainString(),
                        "netAmount", netAmount.toPlainString(),
                        "commissionsCount", String.valueOf(commissions.size()),
                        "commissionType", CommissionType.SUBSCRIPTION.name()
                )
        );
        financialAuditService.record(
                "WALLET_CREDIT",
                payoutOperation.getId().toString(),
                accountHolderId,
                netAmount,
                Map.of(
                        "batchId", batch.getId().toString(),
                        "weekId", String.valueOf(batch.getWeekId()),
                        "grossAmount", grossAmount.toPlainString(),
                        "taxAmount", taxAmount.toPlainString(),
                        "commissionType", CommissionType.SUBSCRIPTION.name()
                )
        );
        return commissions.size();
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
        Wallet beneficiaryWallet = requireActiveWallet(item.getAccountHolderId());
        String currency = commission.getCurrency();

        Operation payoutOperation = resolveOrCreateConfirmedPayoutOperation(batch, item, commission, currency);
        accountingPostingService.postCommissionPayout(
                payoutOperation,
                item.getAccountHolderId(),
                item.getTaxAmount(),
                item.getNetAmount()
        );
        domainEventPublisher.publish(WalletCreditedEvent.create(
                beneficiaryWallet.getId(),
                item.getAccountHolderId(),
                item.getNetAmount(),
                currency,
                payoutOperation.getId()
        ));

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
        if (commission.getCommissionType() == CommissionType.GROUP_BONUS) {
            domainEventPublisher.publish(BonusPaidEvent.create(
                    item.getAccountHolderId(),
                    item.getOperationId(),
                    item.getGrossAmount(),
                    item.getTaxAmount(),
                    item.getNetAmount(),
                    currency
            ));
        }

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

    private Wallet requireActiveWallet(UUID accountHolderId) {
        Wallet wallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Active wallet not found for account holder"));
        if (wallet.getId() == null) {
            throw new BusinessException("Invalid wallet state for payout");
        }
        return wallet;
    }

    private Operation resolveOrCreateGroupedPayoutOperation(int weekId,
                                                            UUID accountHolderId,
                                                            BigDecimal grossAmount,
                                                            String currency) {
        String idempotencyKey = "commission-payout-subscription:" + weekId + ":" + accountHolderId;
        Operation initiated = operationService.createInitiatedOperation(
                accountHolderId,
                accountHolderId,
                OperationType.COMMISSION_PAYOUT,
                grossAmount,
                currency,
                idempotencyKey,
                "Subscription commission payout week " + weekId
        );
        return initiated.getStatus() == OperationStatus.CONFIRMED
                ? initiated
                : operationService.confirmOperation(initiated.getId());
    }

    private BigDecimal resolveTaxRate(UUID accountHolderId) {
        String countryCode = accountHolderRepository.findById(accountHolderId)
                .map(accountHolder -> {
                    if (accountHolder.getServiceCountryCode() != null && !accountHolder.getServiceCountryCode().isBlank()) {
                        return accountHolder.getServiceCountryCode();
                    }
                    return accountHolder.getCountryCode();
                })
                .orElse(null);
        if (countryCode != null && !countryCode.isBlank()) {
            var countryGeneral = countryConfigResolver.resolveActiveConfig(countryCode, CountryConfigType.GENERAL);
            var payload = countryConfigResolver.payloadAsObjectNode(countryGeneral);
            var taxNode = payload.get("taxRate");
            if (taxNode != null && taxNode.isNumber()) {
                return taxNode.decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
            }
        }
        var platformGeneral = countryConfigResolver.resolveActivePlatformConfig(PlatformConfigType.GLOBAL_GENERAL);
        var platformPayload = countryConfigResolver.payloadAsObjectNode(platformGeneral);
        var fallbackTaxNode = platformPayload.get("taxRate");
        if (fallbackTaxNode != null && fallbackTaxNode.isNumber()) {
            return fallbackTaxNode.decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
        }
        return taxRatePercent.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private List<AmountSplit> splitAcrossCommissions(List<MlmCommission> commissions, BigDecimal totalTaxAmount) {
        List<AmountSplit> splits = new java.util.ArrayList<>(commissions.size());
        BigDecimal allocatedTax = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        for (int i = 0; i < commissions.size(); i++) {
            MlmCommission commission = commissions.get(i);
            BigDecimal grossAmount = commission.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal taxAmount = i == commissions.size() - 1
                    ? totalTaxAmount.subtract(allocatedTax).setScale(SCALE, RoundingMode.HALF_UP)
                    : grossAmount.multiply(resolveTaxRate(commission.getBeneficiaryAccountHolderId()))
                    .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
            if (taxAmount.compareTo(grossAmount) > 0) {
                taxAmount = grossAmount;
            }
            allocatedTax = allocatedTax.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP);
            splits.add(new AmountSplit(
                    grossAmount,
                    taxAmount,
                    grossAmount.subtract(taxAmount).setScale(SCALE, RoundingMode.HALF_UP)
            ));
        }
        return splits;
    }

    private int currentWeekId() {
        OffsetDateTime now = OffsetDateTime.now();
        return (now.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100)
                + now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
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

    private record AmountSplit(
            BigDecimal grossAmount,
            BigDecimal taxAmount,
            BigDecimal netAmount
    ) {
    }
}
