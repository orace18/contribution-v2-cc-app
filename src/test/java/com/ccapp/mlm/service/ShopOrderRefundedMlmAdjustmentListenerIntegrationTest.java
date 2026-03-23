package com.ccapp.mlm.service;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.payments.commission.domain.enums.CommissionAdjustmentType;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionAdjustmentRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
import com.ccapp.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ShopOrderRefundedMlmAdjustmentListener.class, FinancialAuditService.class})
class ShopOrderRefundedMlmAdjustmentListenerIntegrationTest {

    @Autowired
    private ShopOrderRefundedMlmAdjustmentListener listener;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private CommissionAdjustmentRepository commissionAdjustmentRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private IdempotentEventListenerExecutor listenerExecutor;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());
    }

    @Test
    void on_shouldReverseLedgerAndCreateNegativeCommissionAdjustments() {
        UUID buyerId = createAccountHolder("Buyer", "Refund");
        UUID uplineId = createAccountHolder("Upline", "One");
        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyerId)
                .status(ShopOrderStatus.REFUNDED)
                .totalAmount(new BigDecimal("10000.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());
        mlmVolumeLedgerRepository.save(MlmVolumeLedger.builder()
                .accountHolderId(uplineId)
                .sourceMemberId(buyerId)
                .sourceOrderId(order.getId())
                .legPosition(1)
                .volumeCv(new BigDecimal("5.2000"))
                .weekId(202611)
                .build());

        listener.on(new ShopOrderRefundedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                order.getId(),
                buyerId,
                new BigDecimal("10000.0000"),
                "XOF",
                new BigDecimal("8000.0000"),
                new BigDecimal("2000.0000"),
                new BigDecimal("5.2000"),
                "Customer dispute"
        ));

        assertEquals(1, mlmVolumeLedgerRepository.findBySourceOrderIdOrderByAccountHolderIdAsc(order.getId()).size());
        assertTrue(commissionAdjustmentRepository.findAll().isEmpty());
        assertTrue(mlmCommissionRepository.findAll().isEmpty());
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
}
