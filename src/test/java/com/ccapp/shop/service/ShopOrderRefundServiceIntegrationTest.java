package com.ccapp.shop.service;

import com.ccapp.financial.audit.domain.entity.FinancialAuditLog;
import com.ccapp.financial.audit.repository.FinancialAuditLogRepository;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.service.MlmVolumeLedgerService;
import com.ccapp.payments.commission.domain.enums.CommissionAdjustmentType;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionAdjustmentRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ShopOrderRefundService.class, MlmVolumeLedgerService.class, FinancialAuditService.class})
class ShopOrderRefundServiceIntegrationTest {

    @Autowired
    private ShopOrderRefundService shopOrderRefundService;
    @Autowired
    private MlmVolumeLedgerService mlmVolumeLedgerService;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private CommissionAdjustmentRepository commissionAdjustmentRepository;
    @Autowired
    private FinancialAuditLogRepository financialAuditLogRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private ShopCommissionPoolRepository shopCommissionPoolRepository;

    @MockBean
    private OperationService operationService;
    @MockBean
    private AccountingPostingService accountingPostingService;

    @Test
    void processRefund_shouldReverseVolumeAndCreateNegativeCommissionAdjustments() {
        UUID upline = createAccountHolder("Upline", "One");
        UUID buyer = createAccountHolder("Buyer", "One");
        OffsetDateTime now = OffsetDateTime.now();

        seedRelationship(upline, upline, 0, now.minusMinutes(4));
        seedRelationship(buyer, buyer, 0, now.minusMinutes(4));
        seedRelationship(upline, buyer, 1, now.minusMinutes(3));

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyer)
                .status(ShopOrderStatus.REFUNDED)
                .totalAmount(new BigDecimal("1200.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .refundedAt(now)
                .build());
        shopCommissionPoolRepository.save(ShopCommissionPool.builder()
                .order(order)
                .merchantAmount(new BigDecimal("900.0000"))
                .platformCommission(new BigDecimal("300.0000"))
                .mlmVolume(new BigDecimal("120.0000"))
                .build());

        mlmVolumeLedgerService.rebuildForDeliveredOrder(
                buyer,
                order.getId(),
                new BigDecimal("120.0000"),
                now.minusMinutes(2)
        );
        assertEquals(1, mlmVolumeLedgerRepository.count());

        shopOrderRefundService.processRefund(ShopOrderRefundedEvent.create(
                order.getId(),
                buyer,
                order.getTotalAmount(),
                order.getCurrency(),
                new BigDecimal("900.0000"),
                new BigDecimal("300.0000"),
                new BigDecimal("120.0000")
        ));

        assertEquals(0, mlmVolumeLedgerRepository.count());
        assertEquals(1, mlmCommissionRepository.count());
        var reversal = mlmCommissionRepository.findAll().getFirst();
        assertEquals(CommissionType.REFUND_REVERSAL, reversal.getCommissionType());
        assertTrue(reversal.getAmount().compareTo(BigDecimal.ZERO) < 0);

        var adjustments = commissionAdjustmentRepository.findByReferenceOrderIdOrderByCreatedAtAsc(order.getId());
        assertTrue(adjustments.stream().anyMatch(a -> a.getAdjustmentType() == CommissionAdjustmentType.MLM_COMMISSION_REVERSAL));
        assertTrue(adjustments.stream().anyMatch(a -> a.getAdjustmentType() == CommissionAdjustmentType.MLM_VOLUME_REVERSAL));

        var auditLogs = financialAuditLogRepository.findAll().stream().map(FinancialAuditLog::getEventType).toList();
        assertTrue(auditLogs.contains("SHOP_ORDER_REFUND"));
        assertTrue(auditLogs.contains("MLM_COMMISSION_REVERSAL"));
        assertTrue(auditLogs.contains("MLM_VOLUME_LEDGER_REVERSED"));
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private void seedRelationship(UUID ancestorId, UUID descendantId, int level, OffsetDateTime createdAt) {
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .createdAt(createdAt)
                .build());
    }
}
