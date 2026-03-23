package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.mlm.domain.entity.MlmLedgerRebuildJob;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.domain.enums.MlmLedgerRebuildJobStatus;
import com.ccapp.mlm.repository.MlmLedgerRebuildJobRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({MlmLedgerRebuildService.class, MlmVolumeLedgerService.class})
class MlmLedgerRebuildServiceIntegrationTest {

    @Autowired
    private MlmLedgerRebuildService rebuildService;
    @Autowired
    private MlmLedgerRebuildJobRepository rebuildJobRepository;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private ShopCommissionPoolRepository shopCommissionPoolRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    @Test
    void rebuild_shouldRegenerateVolumeLedgerInBatches() {
        UUID upline = createAccountHolder("A", "Upline");
        UUID buyer = createAccountHolder("B", "Buyer");
        seedRelationship(upline, upline, 0);
        seedRelationship(buyer, buyer, 0);
        seedRelationship(upline, buyer, 1);

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyer)
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("10000.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());
        shopCommissionPoolRepository.save(ShopCommissionPool.builder()
                .order(order)
                .merchantAmount(new BigDecimal("8000.0000"))
                .platformCommission(new BigDecimal("2000.0000"))
                .mlmVolume(new BigDecimal("5.2000"))
                .build());

        mlmVolumeLedgerRepository.save(MlmVolumeLedger.builder()
                .accountHolderId(createAccountHolder("Legacy", "Node"))
                .sourceMemberId(buyer)
                .sourceOrderId(order.getId())
                .legPosition(1)
                .volumeCv(new BigDecimal("99.0000"))
                .weekId(202610)
                .build());

        MlmLedgerRebuildJob job = rebuildService.requestRebuild(UUID.randomUUID(), 50);
        int processedJobs = rebuildService.processPendingJobs();

        assertEquals(1, processedJobs);
        MlmLedgerRebuildJob refreshed = rebuildJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(MlmLedgerRebuildJobStatus.COMPLETED, refreshed.getStatus());
        assertEquals(1L, refreshed.getProcessedOrders());

        List<MlmVolumeLedger> ledgerEntries = mlmVolumeLedgerRepository.findBySourceOrderIdOrderByAccountHolderIdAsc(order.getId());
        assertEquals(1, ledgerEntries.size());
        assertEquals(upline, ledgerEntries.getFirst().getAccountHolderId());
        assertEquals(0, ledgerEntries.getFirst().getVolumeCv().compareTo(new BigDecimal("5.2000")));
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

    private void seedRelationship(UUID ancestorId, UUID descendantId, int level) {
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build());
    }
}
