package com.ccapp.shop.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.CheckoutItemRequest;
import com.ccapp.shop.dto.CheckoutRequest;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.dto.UpdateShopOrderStatusRequest;
import com.ccapp.shop.event.MarketplaceCommissionAllocatedEvent;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import com.ccapp.shop.event.ShopOrderPaidEvent;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.service.WalletDebitFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Autowired
    private WalletRepository walletRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private CountryConfigResolver countryConfigResolver;
    @MockBean
    private OperationService operationService;
    @MockBean
    private AccountingPostingService accountingPostingService;
    @MockBean
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @MockBean
    private WalletDebitFacade walletDebitFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
        CountryConfigResolver.ResolvedConfig countryGeneral = new CountryConfigResolver.ResolvedConfig(
                "BJ",
                CountryConfigType.GENERAL,
                1,
                "{}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        CountryConfigResolver.ResolvedPlatformConfig platformGeneral = new CountryConfigResolver.ResolvedPlatformConfig(
                PlatformConfigType.GLOBAL_GENERAL,
                1,
                "{}"
        );
        ObjectNode countryPayload = objectMapper.createObjectNode();
        ObjectNode platformPayload = objectMapper.createObjectNode();
        when(countryConfigResolver.resolveActiveConfig(any(), eq(CountryConfigType.GENERAL))).thenReturn(countryGeneral);
        when(countryConfigResolver.resolveActivePlatformConfig(PlatformConfigType.GLOBAL_GENERAL)).thenReturn(platformGeneral);
        when(countryConfigResolver.payloadAsObjectNode(countryGeneral)).thenReturn(countryPayload);
        when(countryConfigResolver.payloadAsObjectNode(platformGeneral)).thenReturn(platformPayload);
        when(countryChartOfAccountsService.resolveSystemAccount(any(), eq(CountryAccountSystemTag.MARKETPLACE_POOL)))
                .thenReturn(CountryChartAccount.builder()
                        .countryCode("BJ")
                        .accountCode("211000")
                        .accountNumber("211000")
                        .accountName("Marketplace Pool")
                        .accountGroup(CountryAccountGroup.MARKETPLACE_POOL)
                        .accountType(CountryChartAccountType.LIABILITY)
                        .currency("XOF")
                        .openingBalance(BigDecimal.ZERO)
                        .status(CountryChartAccountStatus.ACTIVE)
                        .build());
        when(walletDebitFacade.executeSecureDebit(any()))
                .thenReturn(new WalletDebitFacade.WalletDebitExecutionResult(
                        true,
                        false,
                        UUID.randomUUID(),
                        "CONFIRMED",
                        null,
                        null
                ));
        when(operationService.createInitiatedOperation(any(), any(), any(OperationType.class), any(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> Operation.builder()
                        .id(UUID.randomUUID())
                        .accountHolderId(invocation.getArgument(0))
                        .actorUserId(invocation.getArgument(1))
                        .type(invocation.getArgument(2))
                        .status(OperationStatus.CONFIRMED)
                        .amount(invocation.getArgument(3))
                        .currency(invocation.getArgument(4))
                        .idempotencyKey(invocation.getArgument(5))
                        .description(invocation.getArgument(6))
                        .build());
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
                        "WALLET",
                        UUID.randomUUID()
                )
        );

        assertEquals(1L, shopOrderRepository.count());
        assertEquals(1L, shopOrderItemRepository.count());
        assertEquals(1L, shopCommissionPoolRepository.count());
        assertEquals(ShopOrderStatus.CREATED, response.status());
        assertEquals(0, response.totalAmount().compareTo(new BigDecimal("20000.0000")));

        ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(response.id()).orElseThrow();
        assertEquals(0, pool.getPlatformCommission().compareTo(BigDecimal.ZERO.setScale(4)));
        assertEquals(0, pool.getMerchantAmount().compareTo(BigDecimal.ZERO.setScale(4)));
        assertEquals(0, pool.getMlmVolume().compareTo(BigDecimal.ZERO.setScale(4)));

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
                        "WALLET",
                        UUID.randomUUID()
                )
        );

        when(operationService.findByIdempotencyKey("marketplace-payment:" + created.id()))
                .thenReturn(Optional.of(Operation.builder()
                        .id(UUID.randomUUID())
                        .accountHolderId(buyerId)
                        .actorUserId(buyerId)
                        .type(OperationType.MARKETPLACE_PAYMENT)
                        .status(OperationStatus.CONFIRMED)
                        .amount(new BigDecimal("10000.0000"))
                        .currency("XOF")
                        .idempotencyKey("marketplace-payment:" + created.id())
                        .build()));

        shopOrderService.updateStatus(created.id(), buyerId, new UpdateShopOrderStatusRequest(ShopOrderStatus.PAID, null, null, null));
        shopOrderService.updateStatus(created.id(), buyerId, new UpdateShopOrderStatusRequest(ShopOrderStatus.SHIPPED, null, null, null));
        shopOrderService.updateStatus(created.id(), buyerId, new UpdateShopOrderStatusRequest(ShopOrderStatus.DELIVERED, null, null, null));
        shopOrderService.updateStatus(created.id(), buyerId, new UpdateShopOrderStatusRequest(ShopOrderStatus.DELIVERED, null, null, null));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        Mockito.verify(domainEventPublisher, Mockito.times(3)).publish(eventCaptor.capture());

        List<DomainEvent> events = eventCaptor.getAllValues();
        assertTrue(events.stream().anyMatch(ShopOrderPaidEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(MarketplaceCommissionAllocatedEvent.class::isInstance));
        ShopOrderDeliveredEvent deliveredEvent = events.stream()
                .filter(ShopOrderDeliveredEvent.class::isInstance)
                .map(ShopOrderDeliveredEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(created.id(), deliveredEvent.orderId());
        assertEquals(buyerId, deliveredEvent.buyerId());
        assertEquals(0, deliveredEvent.platformCommission().compareTo(new BigDecimal("2000.0000")));
        assertEquals(0, deliveredEvent.merchantAmount().compareTo(new BigDecimal("8000.0000")));
        assertEquals(0, deliveredEvent.mlmVolume().compareTo(new BigDecimal("2000.0000")));

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
                        "WALLET",
                        UUID.randomUUID()
                )
        );

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> shopOrderService.updateStatus(
                        created.id(),
                        buyerId,
                        new UpdateShopOrderStatusRequest(ShopOrderStatus.DELIVERED, null, null, null)
                )
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
        walletRepository.save(Wallet.builder()
                .accountHolderId(saved.getId())
                .provider("TEST")
                .mobileMoneyNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .build());
        return saved.getId();
    }
}
