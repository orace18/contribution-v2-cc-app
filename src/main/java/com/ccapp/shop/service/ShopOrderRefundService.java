package com.ccapp.shop.service;

import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.payments.commission.domain.entity.CommissionAdjustment;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionAdjustmentType;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionAdjustmentRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.wallet.event.WalletCreditedEvent;
import com.ccapp.wallet.service.WalletDebitFacade;
import com.ccapp.common.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopOrderRefundService {

    private final OperationRepository operationRepository;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final CommissionAdjustmentRepository commissionAdjustmentRepository;
    private final FinancialAuditService financialAuditService;
    private final AccountHolderRepository accountHolderRepository;
    private final WalletRepository walletRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final ShopOrderRepository shopOrderRepository;
    private final WalletDebitFacade walletDebitFacade;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public void processRefund(ShopOrderRefundedEvent event) {
        reverseMerchantPayoutIfPossible(event);
        reverseCommissionAllocationIfPossible(event);
        refundBuyerWalletIfApplicable(event);

        List<MlmVolumeLedger> ledgerEntries = mlmVolumeLedgerRepository.findBySourceOrderIdOrderByAccountHolderIdAsc(event.orderId());
        Map<UUID, ReversalAggregate> reversalByAccountHolder = aggregateByAccountHolder(ledgerEntries);
        for (Map.Entry<UUID, ReversalAggregate> entry : reversalByAccountHolder.entrySet()) {
            UUID accountHolderId = entry.getKey();
            ReversalAggregate aggregate = entry.getValue();
            createMlmCommissionReversalIfAbsent(event, accountHolderId, aggregate.totalVolume().negate(), aggregate.weekId());
            if (!commissionAdjustmentRepository.existsByReferenceOrderIdAndAccountHolderIdAndAdjustmentType(
                    event.orderId(),
                    accountHolderId,
                    CommissionAdjustmentType.MLM_VOLUME_REVERSAL
            )) {
                commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                        .referenceOrderId(event.orderId())
                        .accountHolderId(accountHolderId)
                        .adjustmentType(CommissionAdjustmentType.MLM_VOLUME_REVERSAL)
                        .amount(scale(aggregate.totalVolume().negate()))
                        .build());
            }
        }

        BigDecimal deletedVolume = ledgerEntries.stream()
                .map(MlmVolumeLedger::getVolumeCv)
                .map(this::scale)
                .reduce(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), BigDecimal::add);
        int deletedRows = mlmVolumeLedgerRepository.deleteBySourceOrderId(event.orderId());
        financialAuditService.record(
                "MLM_VOLUME_LEDGER_REVERSED",
                event.orderId().toString(),
                null,
                scale(deletedVolume.negate()),
                Map.of("rowsDeleted", String.valueOf(deletedRows))
        );
        financialAuditService.record(
                "SHOP_ORDER_REFUND",
                event.orderId().toString(),
                event.buyerId(),
                scale(event.totalAmount()),
                Map.of(
                        "currency", event.currency(),
                        "merchantAmount", scale(event.merchantAmount()).toPlainString(),
                        "platformCommission", scale(event.platformCommission()).toPlainString(),
                        "businessVolume", scale(event.mlmVolume()).toPlainString()
                )
        );
    }

    private void reverseMerchantPayoutIfPossible(ShopOrderRefundedEvent event) {
        String merchantPayoutKey = "marketplace-merchant-payout:" + event.orderId();
        Operation originalPayout = operationRepository.findByIdempotencyKey(merchantPayoutKey).orElse(null);
        if (originalPayout == null || originalPayout.getStatus() != OperationStatus.CONFIRMED) {
            financialAuditService.record(
                    "MERCHANT_PAYOUT_REVERSAL_SKIPPED",
                    event.orderId().toString(),
                    null,
                    scale(event.merchantAmount().negate()),
                    Map.of("reason", "ORIGINAL_PAYOUT_NOT_FOUND_OR_NOT_CONFIRMED")
            );
            return;
        }

        String reversalKey = "marketplace-merchant-payout-reversal:" + event.orderId();
        String countryCode = resolveCountryCode(originalPayout.getAccountHolderId());
        String marketplacePoolCode = countryChartOfAccountsService
                .resolveSystemAccount(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL)
                .getAccountCode();

        WalletDebitFacade.WalletDebitExecutionResult reversal = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        originalPayout.getAccountHolderId(),
                        originalPayout.getAccountHolderId(),
                        originalPayout.getActorUserId() != null ? originalPayout.getActorUserId() : originalPayout.getAccountHolderId(),
                        null,
                        OperationType.MARKETPLACE_MERCHANT_PAYOUT_REVERSAL,
                        scale(event.merchantAmount()),
                        originalPayout.getCurrency(),
                        reversalKey,
                        "Marketplace merchant payout reversal for refund order " + event.orderId(),
                        "marketplace-merchant-payout-reversal|"
                                + event.orderId()
                                + "|" + scale(event.merchantAmount()).stripTrailingZeros().toPlainString()
                                + "|" + originalPayout.getCurrency().trim().toUpperCase(Locale.ROOT)
                                + "|" + marketplacePoolCode,
                        marketplacePoolCode,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        false,
                        "Marketplace merchant payout reversal debit",
                        "Marketplace pool recovery",
                        null,
                        null,
                        WalletDebitFacade.SecureDebitPostingMode.STANDARD,
                        null
                )
        );
        Operation confirmedReversal = operationService.getById(reversal.operationId());
        if (!commissionAdjustmentRepository.existsByReferenceOrderIdAndAdjustmentType(
                event.orderId(),
                CommissionAdjustmentType.MERCHANT_PAYOUT_REVERSAL
        )) {
            commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                    .referenceOrderId(event.orderId())
                    .accountHolderId(originalPayout.getAccountHolderId())
                    .adjustmentType(CommissionAdjustmentType.MERCHANT_PAYOUT_REVERSAL)
                    .amount(scale(event.merchantAmount().negate()))
                    .build());
        }
        financialAuditService.record(
                "MERCHANT_PAYOUT_REVERSAL",
                event.orderId().toString(),
                originalPayout.getAccountHolderId(),
                scale(event.merchantAmount().negate()),
                Map.of(
                        "originalOperationId", originalPayout.getId().toString(),
                        "reversalOperationId", confirmedReversal.getId().toString()
                )
        );
    }

    private void reverseCommissionAllocationIfPossible(ShopOrderRefundedEvent event) {
        String allocationKey = "marketplace-commission-allocation:" + event.orderId();
        Operation originalAllocation = operationRepository.findByIdempotencyKey(allocationKey).orElse(null);
        if (originalAllocation == null || originalAllocation.getStatus() != OperationStatus.CONFIRMED) {
            return;
        }
        String reversalKey = "marketplace-commission-allocation-reversal:" + event.orderId();
        Operation reversal = operationService.createInitiatedOperation(
                originalAllocation.getAccountHolderId(),
                originalAllocation.getActorUserId(),
                OperationType.MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL,
                scale(event.platformCommission()),
                originalAllocation.getCurrency(),
                reversalKey,
                "Marketplace commission allocation reversal for refund order " + event.orderId()
        );
        Operation confirmedReversal = reversal.getStatus() == OperationStatus.CONFIRMED
                ? reversal
                : operationService.confirmOperation(reversal.getId());
        accountingPostingService.postMarketplaceCommissionAllocationReversal(
                confirmedReversal,
                resolveCountryCode(originalAllocation.getAccountHolderId())
        );
        financialAuditService.record(
                "MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL",
                event.orderId().toString(),
                originalAllocation.getAccountHolderId(),
                scale(event.platformCommission().negate()),
                Map.of(
                        "originalOperationId", originalAllocation.getId().toString(),
                        "reversalOperationId", confirmedReversal.getId().toString()
                )
        );
    }

    private void refundBuyerWalletIfApplicable(ShopOrderRefundedEvent event) {
        var order = shopOrderRepository.findById(event.orderId()).orElse(null);
        if (order == null || order.getPaymentMethod() == null
                || !"WALLET".equalsIgnoreCase(order.getPaymentMethod().trim())) {
            return;
        }

        AccountHolder buyer = accountHolderRepository.findById(event.buyerId())
                .orElseThrow(() -> new IllegalStateException("Buyer account holder not found"));
        String countryCode = order.getCountryCode() != null && !order.getCountryCode().isBlank()
                ? order.getCountryCode().trim().toUpperCase(Locale.ROOT)
                : resolveCountryCode(event.buyerId());
        String poolAccountCode = countryChartOfAccountsService
                .resolveSystemAccount(countryCode, CountryAccountSystemTag.MARKETPLACE_POOL)
                .getAccountCode();
        String buyerWalletCode = countryChartOfAccountsService.findOrCreateWallet(buyer).getAccountCode();

        Operation refund = operationService.createInitiatedOperation(
                event.buyerId(),
                event.buyerId(),
                OperationType.ADJUSTMENT,
                scale(event.totalAmount()),
                event.currency(),
                "marketplace-buyer-refund:" + event.orderId(),
                "Marketplace buyer refund for order " + event.orderId()
        );
        Operation confirmedRefund = refund.getStatus() == OperationStatus.CONFIRMED
                ? refund
                : operationService.confirmOperation(refund.getId());
        accountingPostingService.postTreasuryTransfer(confirmedRefund, poolAccountCode, buyerWalletCode);
        Wallet buyerWallet = requireActiveWallet(event.buyerId());
        domainEventPublisher.publish(WalletCreditedEvent.create(
                buyerWallet.getId(),
                event.buyerId(),
                scale(event.totalAmount()),
                event.currency(),
                confirmedRefund.getId()
        ));
    }

    private void createMlmCommissionReversalIfAbsent(ShopOrderRefundedEvent event,
                                                     UUID accountHolderId,
                                                     BigDecimal reversalAmount,
                                                     Integer weekId) {
        String source = "SHOP_ORDER_REFUND:" + event.orderId();
        boolean exists = mlmCommissionRepository.findByBeneficiaryAccountHolderIdAndSourceAndCommissionType(
                accountHolderId,
                source,
                CommissionType.REFUND_REVERSAL
        ).isPresent();
        if (exists) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        MlmCommission reversal = mlmCommissionRepository.save(MlmCommission.builder()
                .beneficiaryAccountHolderId(accountHolderId)
                .amount(scale(reversalAmount))
                .currency(event.currency())
                .source(source)
                .commissionType(CommissionType.REFUND_REVERSAL)
                .status(CommissionStatus.PAID)
                .weekId(weekId)
                .paidAt(now)
                .build());
        if (!commissionAdjustmentRepository.existsByReferenceOrderIdAndAccountHolderIdAndAdjustmentType(
                event.orderId(),
                accountHolderId,
                CommissionAdjustmentType.MLM_COMMISSION_REVERSAL
        )) {
            commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                    .referenceOrderId(event.orderId())
                    .accountHolderId(accountHolderId)
                    .adjustmentType(CommissionAdjustmentType.MLM_COMMISSION_REVERSAL)
                    .amount(scale(reversalAmount))
                    .build());
        }
        financialAuditService.record(
                "MLM_COMMISSION_REVERSAL",
                reversal.getId().toString(),
                reversal.getBeneficiaryAccountHolderId(),
                reversal.getAmount(),
                Map.of(
                        "orderId", event.orderId().toString(),
                        "weekId", String.valueOf(weekId)
                )
        );
    }

    private Map<UUID, ReversalAggregate> aggregateByAccountHolder(List<MlmVolumeLedger> ledgerEntries) {
        Map<UUID, ReversalAggregate> aggregates = new HashMap<>();
        for (MlmVolumeLedger ledgerEntry : ledgerEntries) {
            ReversalAggregate current = aggregates.get(ledgerEntry.getAccountHolderId());
            BigDecimal amount = scale(ledgerEntry.getVolumeCv());
            if (current == null) {
                aggregates.put(ledgerEntry.getAccountHolderId(), new ReversalAggregate(amount, ledgerEntry.getWeekId()));
                continue;
            }
            aggregates.put(ledgerEntry.getAccountHolderId(), new ReversalAggregate(
                    scale(current.totalVolume().add(amount)),
                    current.weekId() != null ? current.weekId() : ledgerEntry.getWeekId()
            ));
        }
        return aggregates;
    }

    private String resolveCountryCode(UUID accountHolderId) {
        return accountHolderRepository.findById(accountHolderId)
                .map(accountHolder -> {
                    if (accountHolder.getServiceCountryCode() != null && !accountHolder.getServiceCountryCode().isBlank()) {
                        return accountHolder.getServiceCountryCode().trim().toUpperCase(Locale.ROOT);
                    }
                    if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().isBlank()) {
                        return accountHolder.getCountryCode().trim().toUpperCase(Locale.ROOT);
                    }
                    return "INT";
                })
                .orElse("INT");
    }

    private Wallet requireActiveWallet(UUID accountHolderId) {
        return walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Active wallet not found for account holder"));
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    private record ReversalAggregate(BigDecimal totalVolume, Integer weekId) {
    }
}
