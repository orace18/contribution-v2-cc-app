package com.ccapp.shop.service;

import com.ccapp.financial.audit.service.FinancialAuditService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
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

    @Transactional
    public void processRefund(ShopOrderRefundedEvent event) {
        reverseMerchantPayoutIfPossible(event);

        List<MlmVolumeLedger> ledgerEntries = mlmVolumeLedgerRepository
                .findBySourceOrderIdOrderByAccountHolderIdAsc(event.orderId());
        for (MlmVolumeLedger ledgerEntry : ledgerEntries) {
            createMlmCommissionReversalIfAbsent(event, ledgerEntry);
            commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                    .referenceOrderId(event.orderId())
                    .accountHolderId(ledgerEntry.getAccountHolderId())
                    .adjustmentType(CommissionAdjustmentType.MLM_VOLUME_REVERSAL)
                    .amount(scale(ledgerEntry.getVolumeCv().negate()))
                    .build());
        }

        int deletedRows = mlmVolumeLedgerRepository.deleteBySourceOrderId(event.orderId());
        financialAuditService.record(
                "MLM_VOLUME_LEDGER_REVERSED",
                event.orderId().toString(),
                null,
                scale(event.mlmVolume().negate()),
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
                        "mlmVolume", scale(event.mlmVolume()).toPlainString()
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
        Operation reversal = operationService.createInitiatedOperation(
                originalPayout.getAccountHolderId(),
                originalPayout.getActorUserId(),
                OperationType.ADJUSTMENT,
                scale(event.merchantAmount()),
                originalPayout.getCurrency(),
                reversalKey,
                "Marketplace merchant payout reversal for refund order " + event.orderId()
        );
        Operation confirmedReversal = operationService.confirmOperation(reversal.getId());
        accountingPostingService.postAdjustment(confirmedReversal);
        commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                .referenceOrderId(event.orderId())
                .accountHolderId(originalPayout.getAccountHolderId())
                .adjustmentType(CommissionAdjustmentType.MERCHANT_PAYOUT_REVERSAL)
                .amount(scale(event.merchantAmount().negate()))
                .build());
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

    private void createMlmCommissionReversalIfAbsent(ShopOrderRefundedEvent event, MlmVolumeLedger ledgerEntry) {
        String source = "SHOP_ORDER_REFUND:" + event.orderId();
        boolean exists = mlmCommissionRepository.findByBeneficiaryAccountHolderIdAndSourceAndCommissionType(
                ledgerEntry.getAccountHolderId(),
                source,
                CommissionType.REFUND_REVERSAL
        ).isPresent();
        if (exists) {
            return;
        }

        BigDecimal reversalAmount = scale(ledgerEntry.getVolumeCv().negate());
        OffsetDateTime now = OffsetDateTime.now();
        MlmCommission reversal = mlmCommissionRepository.save(MlmCommission.builder()
                .beneficiaryAccountHolderId(ledgerEntry.getAccountHolderId())
                .amount(reversalAmount)
                .currency(event.currency())
                .source(source)
                .commissionType(CommissionType.REFUND_REVERSAL)
                .status(CommissionStatus.PAID)
                .paidAt(now)
                .build());
        commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                .referenceOrderId(event.orderId())
                .accountHolderId(ledgerEntry.getAccountHolderId())
                .adjustmentType(CommissionAdjustmentType.MLM_COMMISSION_REVERSAL)
                .amount(reversalAmount)
                .build());
        financialAuditService.record(
                "MLM_COMMISSION_REVERSAL",
                reversal.getId().toString(),
                reversal.getBeneficiaryAccountHolderId(),
                reversal.getAmount(),
                Map.of(
                        "orderId", event.orderId().toString(),
                        "weekId", String.valueOf(ledgerEntry.getWeekId())
                )
        );
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}
