package com.ccapp.commerce.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.commerce.domain.entity.CommerceDsbPayout;
import com.ccapp.commerce.domain.entity.CommerceOrder;
import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.commerce.repository.CommerceDsbPayoutRepository;
import com.ccapp.commerce.repository.CommerceOrderRepository;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.service.WalletDebitFacade;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CommerceSettlementService.class, DSBService.class})
class CommerceSettlementServiceIntegrationTest {

    @Autowired
    private CommerceSettlementService commerceSettlementService;
    @Autowired
    private CommerceOrderRepository commerceOrderRepository;
    @Autowired
    private CommerceProductRepository commerceProductRepository;
    @Autowired
    private CommerceDsbPayoutRepository commerceDsbPayoutRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private ShopOrderItemRepository shopOrderItemRepository;
    @Autowired
    private ShopProductRepository shopProductRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private MlmMemberLinkRepository mlmMemberLinkRepository;

    @MockBean
    private OperationService operationService;
    @MockBean
    private AccountingPostingService accountingPostingService;
    @MockBean
    private WalletDebitFacade walletDebitFacade;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    @BeforeEach
    void setUp() {
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
    void applyDeliveredOrderSettlement_shouldCreateSponsorChainPayouts() {
        UUID merchantId = createAccountHolder("Merchant", "Settle");
        UUID buyerId = createAccountHolder("Buyer", "Settle");
        UUID sponsor1 = createAccountHolder("Sponsor", "One");
        UUID sponsor2 = createAccountHolder("Sponsor", "Two");
        UUID sponsor3 = createAccountHolder("Sponsor", "Three");

        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(buyerId)
                .sponsorAccountHolderId(sponsor1)
                .placementAccountHolderId(buyerId)
                .build());
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(sponsor1)
                .sponsorAccountHolderId(sponsor2)
                .placementAccountHolderId(sponsor1)
                .build());
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(sponsor2)
                .sponsorAccountHolderId(sponsor3)
                .placementAccountHolderId(sponsor2)
                .build());
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(sponsor3)
                .sponsorAccountHolderId(null)
                .placementAccountHolderId(sponsor3)
                .build());

        ShopProduct product = shopProductRepository.save(ShopProduct.builder()
                .merchantId(merchantId)
                .name("Pack huile")
                .description("Produit test")
                .price(new BigDecimal("10000.0000"))
                .currency("XOF")
                .commissionPercent(new BigDecimal("20.00"))
                .cvValue(new BigDecimal("5.0000"))
                .active(true)
                .build());
        commerceProductRepository.save(CommerceProduct.builder()
                .productId(product.getId())
                .dsbAmount(new BigDecimal("1000.0000"))
                .build());

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyerId)
                .merchantId(merchantId)
                .countryCode("BJ")
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("10000.0000"))
                .currency("XOF")
                .paymentMethod("WALLET")
                .deliveryAddressId(UUID.randomUUID())
                .deliveredAt(OffsetDateTime.now())
                .build());
        ShopOrderItem item = shopOrderItemRepository.save(ShopOrderItem.builder()
                .order(order)
                .productId(product.getId())
                .quantity(1)
                .unitPrice(new BigDecimal("10000.0000"))
                .commissionPercent(new BigDecimal("20.00"))
                .cvValue(new BigDecimal("500.0000"))
                .build());
        commerceOrderRepository.save(CommerceOrder.builder()
                .orderId(order.getId())
                .buyerAccountHolderId(buyerId)
                .merchantAccountHolderId(merchantId)
                .countryCode("BJ")
                .currency("XOF")
                .grossAmount(new BigDecimal("10000.0000"))
                .discountAmount(BigDecimal.ZERO.setScale(4))
                .netPaidAmount(new BigDecimal("10000.0000"))
                .totalDsbAmount(BigDecimal.ZERO.setScale(4))
                .platformCommission(BigDecimal.ZERO.setScale(4))
                .merchantNetAmount(new BigDecimal("10000.0000"))
                .volumeCv(BigDecimal.ZERO.setScale(4))
                .distributionMode(CommerceDistributionMode.NONE)
                .settlementStatus(CommerceSettlementStatus.PENDING_DELIVERY)
                .build());

        CommerceSettlementService.CommerceSettlementAdjustment result =
                commerceSettlementService.applyDeliveredOrderSettlement(
                        order,
                        List.of(item),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("2000.0000"),
                        new BigDecimal("500.0000"),
                        "BJ"
                );

        CommerceOrder updated = commerceOrderRepository.findById(order.getId()).orElseThrow();
        List<CommerceDsbPayout> payouts = commerceDsbPayoutRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        assertEquals(0, result.merchantNetAmount().compareTo(new BigDecimal("7000.0000")));
        assertEquals(0, result.totalDsbAmount().compareTo(new BigDecimal("1000.0000")));
        assertEquals(CommerceDistributionMode.SPONSOR_CHAIN, result.distributionMode());
        assertEquals(CommerceSettlementStatus.SETTLED, updated.getSettlementStatus());
        assertEquals(0, updated.getMerchantNetAmount().compareTo(new BigDecimal("7000.0000")));
        assertEquals(0, updated.getPlatformCommission().compareTo(new BigDecimal("2000.0000")));
        assertEquals(0, updated.getVolumeCv().compareTo(new BigDecimal("500.0000")));
        assertEquals(3, payouts.size());
        assertNotNull(payouts.get(0).getOperationId());
        assertEquals(0, payouts.get(0).getAmount().compareTo(new BigDecimal("500.0000")));
        assertEquals(0, payouts.get(1).getAmount().compareTo(new BigDecimal("300.0000")));
        assertEquals(0, payouts.get(2).getAmount().compareTo(new BigDecimal("200.0000")));
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
