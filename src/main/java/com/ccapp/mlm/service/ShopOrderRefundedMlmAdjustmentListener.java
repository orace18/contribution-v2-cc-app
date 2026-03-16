package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.payments.commission.domain.entity.CommissionAdjustment;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionAdjustmentType;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionAdjustmentRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopOrderRefundedMlmAdjustmentListener implements DomainEventListener<ShopOrderRefundedEvent> {

    private static final String LISTENER_KEY = "mlm.shop-order-refunded-adjustment";

    private final IdempotentEventListenerExecutor listenerExecutor;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final CommissionAdjustmentRepository commissionAdjustmentRepository;
    private final FinancialAuditService financialAuditService;

    @Override
    @EventListener
    public void on(ShopOrderRefundedEvent event) {
        try {
            listenerExecutor.execute(LISTENER_KEY, event, () -> applyRefundAdjustments(event));
        } catch (RuntimeException ex) {
            log.warn("shop_order_refund_adjustment_failed orderId={}", event.orderId(), ex);
        }
    }

    @Transactional
    protected void applyRefundAdjustments(ShopOrderRefundedEvent event) {
        List<MlmVolumeLedger> ledgerEntries = mlmVolumeLedgerRepository.findBySourceOrderIdOrderByAccountHolderIdAsc(event.orderId());

        if (!commissionAdjustmentRepository.existsByReferenceOrderIdAndAdjustmentType(
                event.orderId(), CommissionAdjustmentType.MERCHANT_PAYOUT_REVERSAL)) {
            commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                    .referenceOrderId(event.orderId())
                    .accountHolderId(null)
                    .adjustmentType(CommissionAdjustmentType.MERCHANT_PAYOUT_REVERSAL)
                    .amount(event.merchantAmount().negate())
                    .build());
            financialAuditService.record(
                    "MERCHANT_PAYOUT_REVERSAL",
                    event.orderId().toString(),
                    null,
                    event.merchantAmount().negate(),
                    Map.of(
                            "buyerId", String.valueOf(event.buyerId()),
                            "currency", String.valueOf(event.currency()),
                            "reason", String.valueOf(event.reason())
                    )
            );
        }

        for (MlmVolumeLedger entry : ledgerEntries) {
            if (!commissionAdjustmentRepository.existsByReferenceOrderIdAndAccountHolderIdAndAdjustmentType(
                    event.orderId(), entry.getAccountHolderId(), CommissionAdjustmentType.MLM_VOLUME_REVERSAL)) {
                commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                        .referenceOrderId(event.orderId())
                        .accountHolderId(entry.getAccountHolderId())
                        .adjustmentType(CommissionAdjustmentType.MLM_VOLUME_REVERSAL)
                        .amount(entry.getVolumeCv().negate())
                        .build());
            }

            if (!commissionAdjustmentRepository.existsByReferenceOrderIdAndAccountHolderIdAndAdjustmentType(
                    event.orderId(), entry.getAccountHolderId(), CommissionAdjustmentType.MLM_COMMISSION_REVERSAL)) {
                MlmCommission reversal = mlmCommissionRepository.save(MlmCommission.builder()
                        .operationId(null)
                        .beneficiaryAccountHolderId(entry.getAccountHolderId())
                        .amount(entry.getVolumeCv().negate())
                        .currency(event.currency())
                        .source("ORDER_REFUND")
                        .commissionType(CommissionType.ADJUSTMENT)
                        .weekId(entry.getWeekId())
                        .status(CommissionStatus.PAID)
                        .paidAt(OffsetDateTime.now())
                        .build());
                commissionAdjustmentRepository.save(CommissionAdjustment.builder()
                        .referenceOrderId(event.orderId())
                        .accountHolderId(entry.getAccountHolderId())
                        .adjustmentType(CommissionAdjustmentType.MLM_COMMISSION_REVERSAL)
                        .amount(reversal.getAmount())
                        .build());
                financialAuditService.record(
                        "COMMISSION_REVERSAL",
                        reversal.getId().toString(),
                        reversal.getBeneficiaryAccountHolderId(),
                        reversal.getAmount(),
                        Map.of(
                                "referenceOrderId", String.valueOf(event.orderId()),
                                "weekId", String.valueOf(reversal.getWeekId()),
                                "reason", String.valueOf(event.reason())
                        )
                );
            }
        }

        int deleted = mlmVolumeLedgerRepository.deleteBySourceOrderId(event.orderId());
        if (deleted > 0) {
            financialAuditService.record(
                    "MLM_VOLUME_LEDGER_REVERSAL",
                    event.orderId().toString(),
                    event.buyerId(),
                    BigDecimal.valueOf(deleted).negate(),
                    Map.of("deletedEntries", String.valueOf(deleted), "reason", String.valueOf(event.reason()))
            );
        }
    }
}
