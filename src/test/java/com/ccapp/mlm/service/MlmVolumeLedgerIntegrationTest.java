package com.ccapp.mlm.service;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import com.ccapp.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.IsoFields;
import java.util.List;
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
@Import({MlmVolumeLedgerService.class, ShopOrderDeliveredMlmVolumeListener.class})
class MlmVolumeLedgerIntegrationTest {

    @Autowired
    private ShopOrderDeliveredMlmVolumeListener listener;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;

    @MockBean
    private IdempotentEventListenerExecutor listenerExecutor;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    @Test
    void onDelivered_shouldWriteVolumeLedgerForUplinesOnly() {
        UUID a = createAccountHolder("A", "Root");
        UUID b = createAccountHolder("B", "Node");
        UUID c = createAccountHolder("C", "Node");
        UUID d = createAccountHolder("D", "Buyer");

        OffsetDateTime t0 = OffsetDateTime.now().minusDays(5);
        seedRelationship(a, a, 0, t0);
        seedRelationship(b, b, 0, t0);
        seedRelationship(c, c, 0, t0);
        seedRelationship(d, d, 0, t0);
        seedRelationship(a, b, 1, t0.plusMinutes(1));
        seedRelationship(a, c, 1, t0.plusMinutes(2));
        seedRelationship(b, d, 1, t0.plusMinutes(3));
        seedRelationship(a, d, 2, t0.plusMinutes(3));

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(d)
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("10000.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());

        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-03-12T10:00:00Z");
        listener.on(new ShopOrderDeliveredEvent(
                UUID.randomUUID(),
                occurredAt,
                order.getId(),
                d,
                new BigDecimal("10000.0000"),
                "XOF",
                new BigDecimal("8000.0000"),
                new BigDecimal("2000.0000"),
                new BigDecimal("5.2000")
        ));

        List<MlmVolumeLedger> entries = mlmVolumeLedgerRepository.findBySourceOrderIdOrderByAccountHolderIdAsc(order.getId());
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.getAccountHolderId().equals(a)));
        assertTrue(entries.stream().anyMatch(e -> e.getAccountHolderId().equals(b)));
        assertEquals(0, entries.get(0).getVolumeCv().compareTo(new BigDecimal("5.2000")));
        assertEquals(0, entries.get(1).getVolumeCv().compareTo(new BigDecimal("5.2000")));

        int expectedWeekId = (occurredAt.get(IsoFields.WEEK_BASED_YEAR) * 100) + occurredAt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        assertEquals(expectedWeekId, entries.get(0).getWeekId());
        assertEquals(expectedWeekId, entries.get(1).getWeekId());

        MlmVolumeLedger bEntry = entries.stream().filter(e -> e.getAccountHolderId().equals(b)).findFirst().orElseThrow();
        MlmVolumeLedger aEntry = entries.stream().filter(e -> e.getAccountHolderId().equals(a)).findFirst().orElseThrow();
        assertEquals(1, bEntry.getGeneration());
        assertEquals(2, aEntry.getGeneration());
    }

    @Test
    void onDelivered_shouldBeIdempotentPerOrder() {
        UUID a = createAccountHolder("A", "Root");
        UUID b = createAccountHolder("B", "Buyer");
        OffsetDateTime now = OffsetDateTime.now();

        seedRelationship(a, a, 0, now.minusMinutes(3));
        seedRelationship(b, b, 0, now.minusMinutes(3));
        seedRelationship(a, b, 1, now.minusMinutes(2));

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(b)
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("5000.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());

        ShopOrderDeliveredEvent firstEvent = new ShopOrderDeliveredEvent(
                UUID.randomUUID(),
                now,
                order.getId(),
                b,
                new BigDecimal("5000.0000"),
                "XOF",
                new BigDecimal("4000.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("2.5000")
        );
        ShopOrderDeliveredEvent duplicateEvent = new ShopOrderDeliveredEvent(
                UUID.randomUUID(),
                now.plusSeconds(2),
                order.getId(),
                b,
                new BigDecimal("5000.0000"),
                "XOF",
                new BigDecimal("4000.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("2.5000")
        );

        listener.on(firstEvent);
        listener.on(duplicateEvent);

        assertEquals(1, mlmVolumeLedgerRepository.count());
    }

    @Test
    void onDelivered_withDifferentEventIdsForSameOrder_shouldStillBeIdempotent() {
        UUID a = createAccountHolder("A", "Root");
        UUID b = createAccountHolder("B", "Buyer");
        OffsetDateTime now = OffsetDateTime.now();

        seedRelationship(a, a, 0, now.minusMinutes(3));
        seedRelationship(b, b, 0, now.minusMinutes(3));
        seedRelationship(a, b, 1, now.minusMinutes(2));

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(b)
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("7000.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());

        listener.on(new ShopOrderDeliveredEvent(
                UUID.randomUUID(),
                now,
                order.getId(),
                b,
                new BigDecimal("7000.0000"),
                "XOF",
                new BigDecimal("5600.0000"),
                new BigDecimal("1400.0000"),
                new BigDecimal("3.5000")
        ));
        listener.on(new ShopOrderDeliveredEvent(
                UUID.randomUUID(),
                now.plusSeconds(2),
                order.getId(),
                b,
                new BigDecimal("7000.0000"),
                "XOF",
                new BigDecimal("5600.0000"),
                new BigDecimal("1400.0000"),
                new BigDecimal("3.5000")
        ));

        assertEquals(1, mlmVolumeLedgerRepository.count());
    }

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());
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
