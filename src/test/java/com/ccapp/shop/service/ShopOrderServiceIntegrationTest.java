package com.ccapp.shop.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.CheckoutItemRequest;
import com.ccapp.shop.dto.CheckoutRequest;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ShopProductService.class, ShopOrderService.class})
class ShopOrderServiceIntegrationTest {

    @Autowired
    private ShopOrderService shopOrderService;
    @Autowired
    private ShopProductRepository shopProductRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private ShopOrderItemRepository shopOrderItemRepository;
    @Autowired
    private ShopCommissionPoolRepository shopCommissionPoolRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    @BeforeEach
    void setUp() {
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void checkout_shouldCreateOrderItemsAndCommissionPool() {
        UUID merchantId = createAccountHolder("Merchant", "One");
        UUID buyerId = createAccountHolder("Buyer", "One");
        ShopProduct product = shopProductRepository.save(ShopProduct.builder()
                .merchantId(merchantId)
                .name("Test Product")
                .description("Product description")
                .price(new BigDecimal("10000.0000"))
                .currency("XOF")
                .commissionPercent(new BigDecimal("20.00"))
                .cvValue(new BigDecimal("1200.0000"))
                .active(true)
                .build());

        ShopOrderResponse response = shopOrderService.checkout(
                buyerId,
                new CheckoutRequest(
                        List.of(new CheckoutItemRequest(product.getId(), 2)),
                        "MOBILE_MONEY",
                        UUID.randomUUID()
                )
        );

        assertEquals(1L, shopOrderRepository.count());
        assertEquals(1L, shopOrderItemRepository.count());
        assertEquals(1L, shopCommissionPoolRepository.count());
        assertEquals(ShopOrderStatus.CREATED, response.status());
        assertEquals(0, response.totalAmount().compareTo(new BigDecimal("20000.0000")));

        ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(response.id()).orElseThrow();
        assertEquals(0, pool.getPlatformCommission().compareTo(new BigDecimal("4000.0000")));
        assertEquals(0, pool.getMerchantAmount().compareTo(new BigDecimal("16000.0000")));
        assertEquals(0, pool.getMlmVolume().compareTo(new BigDecimal("2400.0000")));

        Mockito.verify(domainEventPublisher, Mockito.never()).publish(any());
    }

    @Test
    void updateStatus_shouldPublishDeliveredEventOnlyOnDeliveredTransition() {
        UUID merchantId = createAccountHolder("Merchant", "Two");
        UUID buyerId = createAccountHolder("Buyer", "Two");
        ShopProduct product = shopProductRepository.save(ShopProduct.builder()
                .merchantId(merchantId)
                .name("Second Product")
                .description("Product description")
                .price(new BigDecimal("10000.0000"))
                .currency("XOF")
                .commissionPercent(new BigDecimal("20.00"))
                .cvValue(new BigDecimal("1000.0000"))
                .active(true)
                .build());

        ShopOrderResponse created = shopOrderService.checkout(
                buyerId,
                new CheckoutRequest(
                        List.of(new CheckoutItemRequest(product.getId(), 1)),
                        "CARD",
                        UUID.randomUUID()
                )
        );

        shopOrderService.updateStatus(created.id(), ShopOrderStatus.PAID);
        shopOrderService.updateStatus(created.id(), ShopOrderStatus.SHIPPED);
        shopOrderService.updateStatus(created.id(), ShopOrderStatus.DELIVERED);
        shopOrderService.updateStatus(created.id(), ShopOrderStatus.DELIVERED);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        Mockito.verify(domainEventPublisher, Mockito.times(1)).publish(eventCaptor.capture());

        DomainEvent event = eventCaptor.getValue();
        assertInstanceOf(ShopOrderDeliveredEvent.class, event);
        ShopOrderDeliveredEvent deliveredEvent = (ShopOrderDeliveredEvent) event;
        assertEquals(created.id(), deliveredEvent.orderId());
        assertEquals(buyerId, deliveredEvent.buyerId());
        assertEquals(0, deliveredEvent.mlmVolume().compareTo(new BigDecimal("1200.0000")));

        ShopOrder persisted = shopOrderRepository.findById(created.id()).orElseThrow();
        assertEquals(ShopOrderStatus.DELIVERED, persisted.getStatus());
    }

    @Test
    void updateStatus_shouldRejectInvalidTransition() {
        UUID merchantId = createAccountHolder("Merchant", "Three");
        UUID buyerId = createAccountHolder("Buyer", "Three");
        ShopProduct product = shopProductRepository.save(ShopProduct.builder()
                .merchantId(merchantId)
                .name("Third Product")
                .description("Product description")
                .price(new BigDecimal("5000.0000"))
                .currency("XOF")
                .commissionPercent(new BigDecimal("10.00"))
                .cvValue(new BigDecimal("300.0000"))
                .active(true)
                .build());

        ShopOrderResponse created = shopOrderService.checkout(
                buyerId,
                new CheckoutRequest(
                        List.of(new CheckoutItemRequest(product.getId(), 1)),
                        "CARD",
                        UUID.randomUUID()
                )
        );

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> shopOrderService.updateStatus(created.id(), ShopOrderStatus.DELIVERED)
        );
        assertTrue(ex.getMessage().contains("Invalid order status transition"));
        Mockito.verify(domainEventPublisher, Mockito.never()).publish(any());
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
}
