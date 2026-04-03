package com.ccapp.commerce.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.commerce.domain.entity.CommerceOrder;
import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.domain.entity.CommercePromoCode;
import com.ccapp.commerce.dto.CommerceCheckoutItemRequest;
import com.ccapp.commerce.dto.CommerceCheckoutRequest;
import com.ccapp.commerce.dto.CommerceCreatePromoCodeRequest;
import com.ccapp.commerce.dto.CommerceOrderResponse;
import com.ccapp.commerce.repository.CommerceOrderRepository;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.commerce.repository.CommercePromoCodeRepository;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
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
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.shop.service.ShopOrderService;
import com.ccapp.shop.service.ShopProductService;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.service.WalletDebitFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        ShopProductService.class,
        ShopOrderService.class,
        CommerceMerchantService.class,
        CommercePromoCodeService.class,
        CommerceOrderService.class
})
class CommerceOrderServiceIntegrationTest {

    @Autowired
    private CommerceOrderService commerceOrderService;
    @Autowired
    private CommercePromoCodeService commercePromoCodeService;
    @Autowired
    private ShopProductRepository shopProductRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private CommerceProductRepository commerceProductRepository;
    @Autowired
    private CommerceOrderRepository commerceOrderRepository;
    @Autowired
    private CommercePromoCodeRepository commercePromoCodeRepository;
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
    @MockBean
    private CommerceSettlementService commerceSettlementService;

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
    void checkout_shouldPersistDiscountedCommerceOrderAndIncrementPromoUsage() {
        UUID merchantId = createAccountHolder("Merchant", "Commerce");
        UUID buyerId = createAccountHolder("Buyer", "Commerce");

        ShopProduct product = shopProductRepository.save(ShopProduct.builder()
                .merchantId(merchantId)
                .name("Pack semence")
                .description("Produit test")
                .price(new BigDecimal("10000.0000"))
                .currency("XOF")
                .commissionPercent(new BigDecimal("20.00"))
                .cvValue(new BigDecimal("10.0000"))
                .active(true)
                .build());
        commerceProductRepository.save(CommerceProduct.builder()
                .productId(product.getId())
                .dsbAmount(new BigDecimal("1500.0000"))
                .build());

        commercePromoCodeService.create(
                merchantId,
                new CommerceCreatePromoCodeRequest(
                        "GRACE2026",
                        new BigDecimal("1000.0000"),
                        "BJ",
                        OffsetDateTime.now().plusDays(10),
                        5
                )
        );

        CommerceOrderResponse response = commerceOrderService.checkout(
                buyerId,
                new CommerceCheckoutRequest(
                        List.of(new CommerceCheckoutItemRequest(product.getId(), 2)),
                        "WALLET",
                        UUID.randomUUID(),
                        "GRACE2026"
                )
        );

        ShopOrder persistedOrder = shopOrderRepository.findById(response.orderId()).orElseThrow();
        CommerceOrder commerceOrder = commerceOrderRepository.findById(response.orderId()).orElseThrow();
        CommercePromoCode promoCode = commercePromoCodeRepository.findByCodeIgnoreCase("GRACE2026").orElseThrow();

        assertEquals(0, response.grossAmount().compareTo(new BigDecimal("20000.0000")));
        assertEquals(0, response.discountAmount().compareTo(new BigDecimal("1000.0000")));
        assertEquals(0, response.netPaidAmount().compareTo(new BigDecimal("19000.0000")));
        assertEquals(0, persistedOrder.getTotalAmount().compareTo(new BigDecimal("19000.0000")));
        assertEquals(0, commerceOrder.getGrossAmount().compareTo(new BigDecimal("20000.0000")));
        assertEquals(0, commerceOrder.getDiscountAmount().compareTo(new BigDecimal("1000.0000")));
        assertEquals(0, commerceOrder.getNetPaidAmount().compareTo(new BigDecimal("19000.0000")));
        assertEquals(0, commerceOrder.getTotalDsbAmount().compareTo(new BigDecimal("3000.0000")));
        assertNotNull(commerceOrder.getPromoCodeId());
        assertEquals(1, promoCode.getUsedCount());

        Mockito.verify(domainEventPublisher, Mockito.atLeastOnce()).publish(any());
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
