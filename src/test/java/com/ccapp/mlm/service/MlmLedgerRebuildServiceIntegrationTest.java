package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmRelationshipId;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({MlmVolumeLedgerService.class, MlmLedgerRebuildService.class})
class MlmLedgerRebuildServiceIntegrationTest {

    @Autowired
    private MlmVolumeLedgerService mlmVolumeLedgerService;
    @Autowired
    private MlmLedgerRebuildService mlmLedgerRebuildService;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private ShopCommissionPoolRepository shopCommissionPoolRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @Test
    void rebuildDeliveredOrders_shouldRecomputeUplinesInBatches() {
        UUID oldUpline = createAccountHolder("Old", "Upline");
        UUID newUpline = createAccountHolder("New", "Upline");
        UUID buyer = createAccountHolder("Buyer", "One");
        OffsetDateTime now = OffsetDateTime.now();

        seedRelationship(oldUpline, oldUpline, 0, now.minusMinutes(5));
        seedRelationship(newUpline, newUpline, 0, now.minusMinutes(5));
        seedRelationship(buyer, buyer, 0, now.minusMinutes(5));
        seedRelationship(oldUpline, buyer, 1, now.minusMinutes(4));

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyer)
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("1000.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());
        shopCommissionPoolRepository.save(ShopCommissionPool.builder()
                .order(order)
                .merchantAmount(new BigDecimal("700.0000"))
                .platformCommission(new BigDecimal("300.0000"))
                .mlmVolume(new BigDecimal("120.0000"))
                .build());

        mlmVolumeLedgerService.rebuildForDeliveredOrder(
                buyer,
                order.getId(),
                new BigDecimal("120.0000"),
                now.minusMinutes(3)
        );
        assertEquals(1, mlmVolumeLedgerRepository.count());
        assertEquals(oldUpline, mlmVolumeLedgerRepository.findAll().getFirst().getAccountHolderId());

        mlmRelationshipRepository.deleteById(new MlmRelationshipId(oldUpline, buyer));
        seedRelationship(newUpline, buyer, 1, now.minusMinutes(2));

        MlmLedgerRebuildResult result = mlmLedgerRebuildService.rebuildDeliveredOrders(10);

        assertEquals(1, result.processedOrders());
        assertEquals(1, result.ordersWithCommissionPool());
        List<UUID> uplinesAfterRebuild = mlmVolumeLedgerRepository.findBySourceOrderIdOrderByAccountHolderIdAsc(order.getId())
                .stream()
                .map(entry -> entry.getAccountHolderId())
                .toList();
        assertEquals(1, uplinesAfterRebuild.size());
        assertEquals(newUpline, uplinesAfterRebuild.getFirst());
        assertTrue(result.ledgerEntriesRebuilt() >= 1);
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        AccountHolder saved = accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build());
        return saved.getId();
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
