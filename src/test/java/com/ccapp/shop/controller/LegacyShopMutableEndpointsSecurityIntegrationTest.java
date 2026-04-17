package com.ccapp.shop.controller;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.exception.GlobalExceptionHandler;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.commerce.domain.entity.CommerceMerchant;
import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import com.ccapp.commerce.repository.CommerceMerchantRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.ShopCommissionPoolResponse;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.dto.ShopProductResponse;
import com.ccapp.shop.service.LegacyShopMutationSecurityService;
import com.ccapp.shop.service.ShopOrderService;
import com.ccapp.shop.service.ShopProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyShopMutableEndpointsSecurityIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MERCHANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_ACCOUNT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ADMIN_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PRODUCT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID DELIVERY_ADDRESS_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Mock
    private ShopOrderService shopOrderService;
    @Mock
    private ShopProductService shopProductService;
    @Mock
    private CommerceMerchantRepository commerceMerchantRepository;

    private MockMvc mockMvc;
    private LegacyShopMutationSecurityService legacyShopMutationSecurityService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        legacyShopMutationSecurityService = new LegacyShopMutationSecurityService(shopOrderService, commerceMerchantRepository);
        ReflectionTestUtils.setField(legacyShopMutationSecurityService, "legacyMutableEndpointsEnabled", false);

        ShopOrderController shopOrderController = new ShopOrderController(shopOrderService, legacyShopMutationSecurityService);
        ShopProductController shopProductController = new ShopProductController(shopProductService, legacyShopMutationSecurityService);
        mockMvc = MockMvcBuilders.standaloneSetup(shopOrderController, shopProductController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        when(shopOrderService.findOrder(ORDER_ID)).thenReturn(order(ShopOrderStatus.CREATED));
        when(shopOrderService.checkout(eq(BUYER_ID), any())).thenReturn(orderResponse(ShopOrderStatus.CREATED));
        when(shopOrderService.updateStatus(eq(ORDER_ID), eq(BUYER_ID), any())).thenReturn(orderResponse(ShopOrderStatus.PAID));
        when(shopOrderService.updateStatus(eq(ORDER_ID), eq(MERCHANT_ID), any())).thenReturn(orderResponse(ShopOrderStatus.DELIVERED));
        when(shopOrderService.refundDeliveredOrder(eq(ORDER_ID), any())).thenReturn(orderResponse(ShopOrderStatus.REFUNDED));
        when(shopProductService.createProduct(eq(MERCHANT_ID), any())).thenReturn(productResponse());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whenLegacyMutationsDisabled_productCreateReturns403AndLogsBlocked(CapturedOutput output) throws Exception {
        ResultActions result = performAs(
                accountHolderAuthentication(MERCHANT_ID),
                post("/shop/products").contentType(MediaType.APPLICATION_JSON).content(productCreateJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Legacy shop mutable endpoints are disabled"));

        verify(shopProductService, never()).createProduct(any(), any());
        assertContains(output, "legacy_shop_mutation_blocked action=CREATE_PRODUCT endpoint=/shop/products");
        assertContains(output, "reason=disabled");
    }

    @Test
    void whenLegacyMutationsDisabled_checkoutReturns403AndLogsBlocked(CapturedOutput output) throws Exception {
        ResultActions result = performAs(
                accountHolderAuthentication(BUYER_ID),
                post("/shop/orders/checkout").contentType(MediaType.APPLICATION_JSON).content(checkoutJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Legacy shop mutable endpoints are disabled"));

        verify(shopOrderService, never()).checkout(any(), any());
        assertContains(output, "legacy_shop_mutation_blocked action=CHECKOUT endpoint=/shop/orders/checkout");
        assertContains(output, "actorType=ACCOUNT_HOLDER");
    }

    @Test
    void whenLegacyMutationsDisabled_updateStatusReturns403AndLogsBlocked(CapturedOutput output) throws Exception {
        ResultActions result = performAs(
                accountHolderAuthentication(BUYER_ID),
                patch("/shop/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson(ShopOrderStatus.PAID))
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Legacy shop mutable endpoints are disabled"));

        verify(shopOrderService, never()).updateStatus(any(), any(), any());
        assertContains(output, "legacy_shop_mutation_blocked action=UPDATE_STATUS endpoint=/shop/orders/{orderId}/status");
        assertContains(output, "orderId=" + ORDER_ID);
        assertContains(output, "targetStatus=PAID");
    }

    @Test
    void whenLegacyMutationsDisabled_refundReturns403AndLogsBlocked(CapturedOutput output) throws Exception {
        ResultActions result = performAs(
                accountHolderAuthentication(MERCHANT_ID),
                post("/shop/orders/{orderId}/refund", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Legacy shop mutable endpoints are disabled"));

        verify(shopOrderService, never()).refundDeliveredOrder(any(), any());
        assertContains(output, "legacy_shop_mutation_blocked action=REFUND endpoint=/shop/orders/{orderId}/refund");
        assertContains(output, "orderId=" + ORDER_ID);
    }

    @Test
    void whenEnabled_productCreateAllowsOnlyActiveMerchantAndLogsAuthorization(CapturedOutput output) throws Exception {
        enableLegacyMutations();
        when(commerceMerchantRepository.findByAccountHolderId(MERCHANT_ID))
                .thenReturn(Optional.of(activeMerchant(MERCHANT_ID)));

        ResultActions result = performAs(
                accountHolderAuthentication(MERCHANT_ID),
                post("/shop/products").contentType(MediaType.APPLICATION_JSON).content(productCreateJson())
        );

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID.toString()));

        verify(shopProductService).createProduct(eq(MERCHANT_ID), any());
        assertContains(output, "legacy_shop_mutation_authorized action=CREATE_PRODUCT endpoint=/shop/products");
        assertContains(output, "actorType=MERCHANT");
    }

    @Test
    void whenEnabled_productCreateDeniesNonMerchantAndLogsDenied(CapturedOutput output) throws Exception {
        enableLegacyMutations();
        when(commerceMerchantRepository.findByAccountHolderId(OTHER_ACCOUNT_ID)).thenReturn(Optional.empty());

        ResultActions result = performAs(
                accountHolderAuthentication(OTHER_ACCOUNT_ID),
                post("/shop/products").contentType(MediaType.APPLICATION_JSON).content(productCreateJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("legacy product creation is restricted to active merchants"));

        verify(shopProductService, never()).createProduct(any(), any());
        assertContains(output, "legacy_shop_mutation_denied action=CREATE_PRODUCT endpoint=/shop/products");
        assertContains(output, "reason=legacy product creation is restricted to active merchants");
    }

    @Test
    void whenEnabled_productCreateDeniesAdminAndLogsDenied(CapturedOutput output) throws Exception {
        enableLegacyMutations();

        ResultActions result = performAs(
                adminAuthentication(AdminRole.ROLE_SUPER_ADMIN),
                post("/shop/products").contentType(MediaType.APPLICATION_JSON).content(productCreateJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("legacy product creation is restricted to active merchants; admins must use /api/backoffice/products"));

        verify(shopProductService, never()).createProduct(any(), any());
        assertContains(output, "legacy_shop_mutation_denied action=CREATE_PRODUCT endpoint=/shop/products");
        assertContains(output, "actorType=ADMIN_ROLE_SUPER_ADMIN");
    }

    @Test
    void whenEnabled_checkoutAllowsBuyerAndLogsAuthorization(CapturedOutput output) throws Exception {
        enableLegacyMutations();

        ResultActions result = performAs(
                accountHolderAuthentication(BUYER_ID),
                post("/shop/orders/checkout").contentType(MediaType.APPLICATION_JSON).content(checkoutJson())
        );

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        verify(shopOrderService).checkout(eq(BUYER_ID), any());
        assertContains(output, "legacy_shop_mutation_authorized action=CHECKOUT endpoint=/shop/orders/checkout");
        assertContains(output, "actorType=BUYER");
    }

    @Test
    void whenEnabled_checkoutDeniesAdminAndLogsDenied(CapturedOutput output) throws Exception {
        enableLegacyMutations();

        ResultActions result = performAs(
                adminAuthentication(AdminRole.ROLE_SUPER_ADMIN),
                post("/shop/orders/checkout").contentType(MediaType.APPLICATION_JSON).content(checkoutJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("legacy checkout is restricted to the buyer account"));

        verify(shopOrderService, never()).checkout(any(), any());
        assertContains(output, "legacy_shop_mutation_denied action=CHECKOUT endpoint=/shop/orders/checkout");
        assertContains(output, "actorType=ADMIN_ROLE_SUPER_ADMIN");
    }

    @Test
    void whenEnabled_updateStatusAllowsBuyerPaymentAndLogsAuthorization(CapturedOutput output) throws Exception {
        enableLegacyMutations();

        ResultActions result = performAs(
                accountHolderAuthentication(BUYER_ID),
                patch("/shop/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson(ShopOrderStatus.PAID))
        );

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        verify(shopOrderService).updateStatus(eq(ORDER_ID), eq(BUYER_ID), any());
        assertContains(output, "legacy_shop_mutation_authorized action=UPDATE_STATUS endpoint=/shop/orders/{orderId}/status");
        assertContains(output, "actorType=BUYER");
        assertContains(output, "targetStatus=PAID");
    }

    @Test
    void whenEnabled_updateStatusAllowsMerchantDeliveryAndLogsAuthorization(CapturedOutput output) throws Exception {
        enableLegacyMutations();
        when(shopOrderService.findOrder(ORDER_ID)).thenReturn(order(ShopOrderStatus.SHIPPED));

        ResultActions result = performAs(
                accountHolderAuthentication(MERCHANT_ID),
                patch("/shop/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson(ShopOrderStatus.DELIVERED))
        );

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        verify(shopOrderService).updateStatus(eq(ORDER_ID), eq(MERCHANT_ID), any());
        assertContains(output, "legacy_shop_mutation_authorized action=UPDATE_STATUS endpoint=/shop/orders/{orderId}/status");
        assertContains(output, "actorType=MERCHANT");
        assertContains(output, "targetStatus=DELIVERED");
    }

    @Test
    void whenEnabled_updateStatusDeniesBuyerForDeliveryAndLogsDenied(CapturedOutput output) throws Exception {
        enableLegacyMutations();

        ResultActions result = performAs(
                accountHolderAuthentication(BUYER_ID),
                patch("/shop/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson(ShopOrderStatus.DELIVERED))
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("buyer can only pay or cancel their own legacy order"));

        verify(shopOrderService, never()).updateStatus(eq(ORDER_ID), eq(BUYER_ID), any());
        assertContains(output, "legacy_shop_mutation_denied action=UPDATE_STATUS endpoint=/shop/orders/{orderId}/status");
        assertContains(output, "actorType=BUYER");
        assertContains(output, "targetStatus=DELIVERED");
    }

    @Test
    void whenEnabled_updateStatusDeniesAdminPaidAndLogsDenied(CapturedOutput output) throws Exception {
        enableLegacyMutations();

        ResultActions result = performAs(
                adminAuthentication(AdminRole.ROLE_SUPER_ADMIN),
                patch("/shop/orders/{orderId}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusJson(ShopOrderStatus.PAID))
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("legacy payment status updates must be initiated by the buyer"));

        verify(shopOrderService, never()).updateStatus(any(), any(), any());
        assertContains(output, "legacy_shop_mutation_denied action=UPDATE_STATUS endpoint=/shop/orders/{orderId}/status");
        assertContains(output, "actorType=ADMIN_ROLE_SUPER_ADMIN");
        assertContains(output, "targetStatus=PAID");
    }

    @Test
    void whenEnabled_refundAllowsAdminAndLogsAuthorization(CapturedOutput output) throws Exception {
        enableLegacyMutations();
        when(shopOrderService.findOrder(ORDER_ID)).thenReturn(order(ShopOrderStatus.DELIVERED));

        ResultActions result = performAs(
                adminAuthentication(AdminRole.ROLE_OPERATIONS),
                post("/shop/orders/{orderId}/refund", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundJson())
        );

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        verify(shopOrderService).refundDeliveredOrder(eq(ORDER_ID), any());
        assertContains(output, "legacy_shop_mutation_authorized action=REFUND endpoint=/shop/orders/{orderId}/refund");
        assertContains(output, "actorType=ADMIN_ROLE_OPERATIONS");
        assertContains(output, "orderId=" + ORDER_ID);
    }

    @Test
    void whenEnabled_refundDeniesBuyerAndLogsDenied(CapturedOutput output) throws Exception {
        enableLegacyMutations();
        when(shopOrderService.findOrder(ORDER_ID)).thenReturn(order(ShopOrderStatus.DELIVERED));

        ResultActions result = performAs(
                accountHolderAuthentication(BUYER_ID),
                post("/shop/orders/{orderId}/refund", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundJson())
        );

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("refund is restricted to the merchant owner or an admin on legacy shop endpoints"));

        verify(shopOrderService, never()).refundDeliveredOrder(eq(ORDER_ID), any());
        assertContains(output, "legacy_shop_mutation_denied action=REFUND endpoint=/shop/orders/{orderId}/refund");
        assertContains(output, "actorType=BUYER");
        assertContains(output, "orderId=" + ORDER_ID);
    }

    private void enableLegacyMutations() {
        ReflectionTestUtils.setField(legacyShopMutationSecurityService, "legacyMutableEndpointsEnabled", true);
    }

    private ResultActions performAs(Authentication authentication,
                                    MockHttpServletRequestBuilder requestBuilder) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        return mockMvc.perform(requestBuilder);
    }

    private Authentication accountHolderAuthentication(UUID accountHolderId) {
        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .userId(accountHolderId)
                .accountHolderId(accountHolderId)
                .email(accountHolderId + "@clubchretien.test")
                .build();
        return new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_USER")
        );
    }

    private Authentication adminAuthentication(AdminRole role) {
        AdminPrincipal principal = AdminPrincipal.builder()
                .adminUserId(ADMIN_ID)
                .email("admin@clubchretien.test")
                .role(role)
                .countryCode("BJ")
                .build();
        return new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                AuthorityUtils.createAuthorityList(role.name())
        );
    }

    private ShopOrder order(ShopOrderStatus status) {
        return ShopOrder.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .merchantId(MERCHANT_ID)
                .status(status)
                .totalAmount(new BigDecimal("5000.0000"))
                .currency("XOF")
                .paymentMethod("WALLET")
                .deliveryAddressId(DELIVERY_ADDRESS_ID)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private ShopOrderResponse orderResponse(ShopOrderStatus status) {
        return new ShopOrderResponse(
                ORDER_ID,
                BUYER_ID,
                status,
                new BigDecimal("5000.0000"),
                "XOF",
                "WALLET",
                DELIVERY_ADDRESS_ID,
                OffsetDateTime.now(),
                List.of(),
                new ShopCommissionPoolResponse(
                        BigDecimal.ZERO.setScale(4),
                        BigDecimal.ZERO.setScale(4),
                        BigDecimal.ZERO.setScale(4)
                ),
                null,
                false,
                null,
                null
        );
    }

    private ShopProductResponse productResponse() {
        return new ShopProductResponse(
                PRODUCT_ID,
                MERCHANT_ID,
                "Legacy product",
                "Legacy product description",
                "Books",
                new BigDecimal("4500.0000"),
                "XOF",
                new BigDecimal("10.00"),
                new BigDecimal("100.0000"),
                true,
                "https://cdn.example.com/store/legacy-product.png"
        );
    }

    private CommerceMerchant activeMerchant(UUID merchantAccountHolderId) {
        return CommerceMerchant.builder()
                .id(UUID.randomUUID())
                .accountHolderId(merchantAccountHolderId)
                .displayName("Merchant")
                .countryCode("BJ")
                .status(CommerceMerchantStatus.ACTIVE)
                .build();
    }

    private String productCreateJson() throws Exception {
        return objectMapper.writeValueAsString(new ProductCreatePayload(
                "Legacy product",
                "Legacy product description",
                new BigDecimal("4500.0000"),
                "XOF",
                new BigDecimal("10.00"),
                new BigDecimal("100.0000"),
                true,
                "Books",
                "https://cdn.example.com/store/legacy-product.png"
        ));
    }

    private String checkoutJson() throws Exception {
        return objectMapper.writeValueAsString(new CheckoutPayload(
                List.of(new CheckoutItemPayload(PRODUCT_ID, 1)),
                "WALLET",
                DELIVERY_ADDRESS_ID
        ));
    }

    private String updateStatusJson(ShopOrderStatus status) throws Exception {
        return objectMapper.writeValueAsString(new UpdateStatusPayload(status, null, null, null));
    }

    private String refundJson() throws Exception {
        return objectMapper.writeValueAsString(new RefundPayload("duplicate order"));
    }

    private void assertContains(CapturedOutput output, String fragment) {
        org.junit.jupiter.api.Assertions.assertTrue(
                output.getOut().contains(fragment),
                () -> "Expected log fragment not found: " + fragment + "\nActual logs:\n" + output.getOut()
        );
    }

    private record ProductCreatePayload(String name,
                                        String description,
                                        BigDecimal price,
                                        String currency,
                                        BigDecimal commissionPercent,
                                        BigDecimal cvValue,
                                        Boolean active,
                                        String category,
                                        String imageUrl) {
    }

    private record CheckoutPayload(List<CheckoutItemPayload> items,
                                   String paymentMethod,
                                   UUID deliveryAddressId) {
    }

    private record CheckoutItemPayload(UUID productId, Integer quantity) {
    }

    private record UpdateStatusPayload(ShopOrderStatus status,
                                       String pin,
                                       UUID otpChallengeId,
                                       String otpCode) {
    }

    private record RefundPayload(String reason) {
    }
}
