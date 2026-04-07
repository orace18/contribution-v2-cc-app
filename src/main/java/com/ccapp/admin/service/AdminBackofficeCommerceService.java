package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminBackofficeMerchantRequest;
import com.ccapp.admin.dto.AdminBackofficeMerchantResponse;
import com.ccapp.admin.dto.AdminBackofficeOrderItemResponse;
import com.ccapp.admin.dto.AdminBackofficeOrderResponse;
import com.ccapp.admin.dto.AdminBackofficeProductRequest;
import com.ccapp.admin.dto.AdminBackofficeProductResponse;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.commerce.domain.entity.CommerceMerchant;
import com.ccapp.commerce.domain.entity.CommerceOrder;
import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.commerce.repository.CommerceMerchantRepository;
import com.ccapp.commerce.repository.CommerceOrderRepository;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.CreateShopProductRequest;
import com.ccapp.shop.dto.ShopProductResponse;
import com.ccapp.shop.dto.UpdateShopOrderStatusRequest;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.shop.service.ShopOrderService;
import com.ccapp.shop.service.ShopProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBackofficeCommerceService {

    private final CommerceMerchantRepository commerceMerchantRepository;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CommerceProductRepository commerceProductRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final ShopOrderItemRepository shopOrderItemRepository;
    private final ShopCommissionPoolRepository shopCommissionPoolRepository;
    private final ShopProductRepository shopProductRepository;
    private final ShopOrderService shopOrderService;
    private final ShopProductService shopProductService;
    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AdminBackofficeMerchantResponse> listMerchants(String countryCode, CommerceMerchantStatus status) {
        return commerceMerchantRepository.findAll().stream()
                .filter(merchant -> countryCode == null || countryCode.isBlank()
                        || countryCode.equalsIgnoreCase(merchant.getCountryCode()))
                .filter(merchant -> status == null || status == merchant.getStatus())
                .map(this::toMerchantResponse)
                .toList();
    }

    @Transactional
    public AdminBackofficeMerchantResponse createMerchant(AdminBackofficeMerchantRequest request) {
        UUID accountHolderId = UUID.fromString(request.walletAccountHolderId().trim());
        AccountHolder holder = accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        CommerceMerchant merchant = commerceMerchantRepository.findByAccountHolderId(accountHolderId)
                .orElseGet(() -> CommerceMerchant.builder()
                        .id(accountHolderId)
                        .accountHolderId(accountHolderId)
                        .build());

        merchant.setDisplayName(request.name().trim());
        merchant.setCountryCode(normalizeCountry(request.country(), holder));
        merchant.setStatus(request.status() == null ? CommerceMerchantStatus.ACTIVE : request.status());
        commerceMerchantRepository.save(merchant);
        return toMerchantResponse(merchant);
    }

    @Transactional
    public AdminBackofficeMerchantResponse updateMerchant(UUID merchantId, AdminBackofficeMerchantRequest request) {
        CommerceMerchant merchant = commerceMerchantRepository.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));

        merchant.setDisplayName(request.name().trim());
        merchant.setCountryCode(request.country().trim().toUpperCase(Locale.ROOT));
        merchant.setStatus(request.status());
        commerceMerchantRepository.save(merchant);
        return toMerchantResponse(merchant);
    }

    @Transactional
    public AdminBackofficeMerchantResponse updateMerchantStatus(UUID merchantId, CommerceMerchantStatus status) {
        CommerceMerchant merchant = commerceMerchantRepository.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
        merchant.setStatus(status);
        commerceMerchantRepository.save(merchant);
        return toMerchantResponse(merchant);
    }

    @Transactional(readOnly = true)
    public List<AdminBackofficeProductResponse> listProducts(String countryCode, Boolean active) {
        Map<UUID, CommerceProduct> commerceByProductId = new HashMap<>();
        commerceProductRepository.findAll().forEach(item -> commerceByProductId.put(item.getProductId(), item));

        return shopProductRepository.findAll().stream()
                .filter(product -> countryCode == null || countryCode.isBlank()
                        || countryCode.equalsIgnoreCase(resolveMerchantCountry(product.getMerchantId())))
                .filter(product -> active == null || product.isActive() == active)
                .map(product -> toProductResponse(product, commerceByProductId.get(product.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminBackofficeOrderResponse> listOrders(String countryCode, ShopOrderStatus status, CommerceSettlementStatus settlementStatus) {
        return shopOrderRepository.findAll().stream()
                .filter(order -> countryCode == null || countryCode.isBlank()
                        || countryCode.equalsIgnoreCase(order.getCountryCode()))
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> settlementStatus == null || resolveSettlementStatus(order) == settlementStatus)
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminBackofficeOrderResponse getOrder(UUID orderId) {
        ShopOrder order = shopOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return toOrderResponse(order);
    }

    @Transactional
    public AdminBackofficeProductResponse createProduct(AdminBackofficeProductRequest request) {
        UUID merchantId = UUID.fromString(request.merchantId().trim());
        ShopProductResponse created = shopProductService.createProduct(
                merchantId,
                new CreateShopProductRequest(
                        request.name(),
                        request.description(),
                        request.price(),
                        request.currency(),
                        request.commissionPercent(),
                        request.cvValue(),
                        request.active(),
                        null,
                        null
                )
        );

        CommerceProduct commerceProduct = commerceProductRepository.save(CommerceProduct.builder()
                .productId(created.id())
                .dsbAmount(scale(request.dsbAmount()))
                .build());

        ShopProduct product = shopProductRepository.findById(created.id())
                .orElseThrow(() -> new NotFoundException("Product not found"));
        return toProductResponse(product, commerceProduct);
    }

    @Transactional
    public AdminBackofficeProductResponse updateProduct(UUID productId, AdminBackofficeProductRequest request) {
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        product.setMerchantId(UUID.fromString(request.merchantId().trim()));
        product.setName(request.name().trim());
        product.setDescription(request.description() != null ? request.description().trim() : null);
        product.setPrice(request.price());
        product.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        product.setCommissionPercent(request.commissionPercent());
        product.setCvValue(request.cvValue());
        product.setActive(request.active() == null || request.active());
        shopProductRepository.save(product);

        CommerceProduct commerceProduct = commerceProductRepository.findById(productId)
                .orElseGet(() -> CommerceProduct.builder().productId(productId).build());
        commerceProduct.setDsbAmount(scale(request.dsbAmount()));
        commerceProductRepository.save(commerceProduct);
        return toProductResponse(product, commerceProduct);
    }

    @Transactional
    public AdminBackofficeProductResponse updateProductStatus(UUID productId, boolean active) {
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        product.setActive(active);
        shopProductRepository.save(product);
        CommerceProduct commerceProduct = commerceProductRepository.findById(productId).orElse(null);
        return toProductResponse(product, commerceProduct);
    }

    @Transactional
    public AdminBackofficeOrderResponse confirmOrderDelivery(UUID orderId) {
        ShopOrder order = shopOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        UUID actorUserId = order.getMerchantId() != null ? order.getMerchantId() : order.getBuyerId();
        shopOrderService.updateStatus(orderId, actorUserId, new UpdateShopOrderStatusRequest(ShopOrderStatus.DELIVERED, null, null, null));
        return getOrder(orderId);
    }

    @Transactional
    public AdminBackofficeOrderResponse cancelOrder(UUID orderId) {
        ShopOrder order = shopOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        UUID actorUserId = order.getMerchantId() != null ? order.getMerchantId() : order.getBuyerId();
        shopOrderService.updateStatus(orderId, actorUserId, new UpdateShopOrderStatusRequest(ShopOrderStatus.CANCELLED, null, null, null));
        return getOrder(orderId);
    }

    @Transactional
    public AdminBackofficeOrderResponse refundOrder(UUID orderId) {
        shopOrderService.refundDeliveredOrder(orderId, null);
        return getOrder(orderId);
    }

    private AdminBackofficeMerchantResponse toMerchantResponse(CommerceMerchant merchant) {
        AccountHolder holder = accountHolderRepository.findById(merchant.getAccountHolderId()).orElse(null);
        String contactName = holder == null ? "" : displayName(holder);
        String phone = holder == null ? "" : nullToEmpty(holder.getPhoneNumber());
        String email = resolveEmail(merchant.getAccountHolderId());
        int productsCount = shopProductRepository.findByMerchantIdOrderByNameAsc(merchant.getAccountHolderId()).size();

        return new AdminBackofficeMerchantResponse(
                merchant.getId(),
                merchant.getDisplayName(),
                contactName,
                phone,
                email,
                merchant.getCountryCode(),
                merchant.getAccountHolderId().toString(),
                "",
                merchant.getStatus(),
                productsCount,
                0,
                merchant.getCreatedAt()
        );
    }

    private AdminBackofficeProductResponse toProductResponse(ShopProduct product, CommerceProduct commerceProduct) {
        CommerceMerchant merchant = commerceMerchantRepository.findByAccountHolderId(product.getMerchantId()).orElse(null);
        OffsetDateTime createdAt = commerceProduct != null && commerceProduct.getCreatedAt() != null
                ? commerceProduct.getCreatedAt()
                : OffsetDateTime.now();

        return new AdminBackofficeProductResponse(
                product.getId(),
                product.getMerchantId(),
                merchant != null ? merchant.getDisplayName() : product.getMerchantId().toString(),
                resolveMerchantCountry(product.getMerchantId()),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getCvValue(),
                product.getCommissionPercent(),
                commerceProduct == null || commerceProduct.getDsbAmount() == null ? BigDecimal.ZERO : commerceProduct.getDsbAmount(),
                "DIGITAL",
                "STORE",
                product.isActive() ? "ACTIVE" : "INACTIVE",
                null,
                null,
                createdAt
        );
    }

    private AdminBackofficeOrderResponse toOrderResponse(ShopOrder order) {
        CommerceOrder commerceOrder = commerceOrderRepository.findById(order.getId()).orElse(null);
        ShopCommissionPool commissionPool = shopCommissionPoolRepository.findByOrderId(order.getId()).orElse(null);
        List<ShopOrderItem> items = shopOrderItemRepository.findByOrderId(order.getId());
        Map<UUID, ShopProduct> productsById = new HashMap<>();
        shopProductRepository.findAllById(items.stream().map(ShopOrderItem::getProductId).toList())
                .forEach(product -> productsById.put(product.getId(), product));

        List<AdminBackofficeOrderItemResponse> orderItems = items.stream()
                .map(item -> {
                    ShopProduct product = productsById.get(item.getProductId());
                    BigDecimal totalPrice = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    return new AdminBackofficeOrderItemResponse(
                            item.getProductId(),
                            product != null ? product.getName() : item.getProductId().toString(),
                            item.getQuantity(),
                            item.getUnitPrice(),
                            totalPrice,
                            "STORE"
                    );
                })
                .toList();

        return new AdminBackofficeOrderResponse(
                order.getId(),
                order.getMerchantId(),
                commerceMerchantRepository.findByAccountHolderId(order.getMerchantId()).map(CommerceMerchant::getDisplayName).orElse(""),
                order.getBuyerId(),
                order.getCountryCode(),
                order.getCurrency(),
                commerceOrder != null && commerceOrder.getGrossAmount() != null ? commerceOrder.getGrossAmount() : order.getTotalAmount(),
                commerceOrder != null && commerceOrder.getDiscountAmount() != null ? commerceOrder.getDiscountAmount() : BigDecimal.ZERO,
                commerceOrder != null && commerceOrder.getNetPaidAmount() != null ? commerceOrder.getNetPaidAmount() : order.getTotalAmount(),
                commerceOrder != null && commerceOrder.getTotalDsbAmount() != null ? commerceOrder.getTotalDsbAmount() : BigDecimal.ZERO,
                commerceOrder != null && commerceOrder.getPlatformCommission() != null ? commerceOrder.getPlatformCommission() : commissionPool != null ? commissionPool.getPlatformCommission() : BigDecimal.ZERO,
                commerceOrder != null && commerceOrder.getMerchantNetAmount() != null ? commerceOrder.getMerchantNetAmount() : commissionPool != null ? commissionPool.getMerchantAmount() : BigDecimal.ZERO,
                commerceOrder != null && commerceOrder.getVolumeCv() != null ? commerceOrder.getVolumeCv() : items.stream().map(ShopOrderItem::getCvValue).reduce(BigDecimal.ZERO, BigDecimal::add),
                commerceOrder != null && commerceOrder.getPromoCodeId() != null,
                null,
                mapDistributionMode(commerceOrder != null ? commerceOrder.getDistributionMode() : null),
                resolveSettlementStatus(order).name(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getDeliveredAt(),
                commerceOrder != null ? commerceOrder.getSettledAt() : null,
                null,
                orderItems
        );
    }

    private String resolveMerchantCountry(UUID merchantId) {
        CommerceMerchant merchant = commerceMerchantRepository.findByAccountHolderId(merchantId).orElse(null);
        if (merchant != null && merchant.getCountryCode() != null) {
            return merchant.getCountryCode();
        }
        AccountHolder holder = accountHolderRepository.findById(merchantId).orElse(null);
        return normalizeCountry(null, holder);
    }

    private String normalizeCountry(String requestedCountry, AccountHolder holder) {
        if (requestedCountry != null && !requestedCountry.isBlank()) {
            return requestedCountry.trim().toUpperCase(Locale.ROOT);
        }
        if (holder == null) {
            return "CI";
        }
        if (holder.getServiceCountryCode() != null && !holder.getServiceCountryCode().isBlank()) {
            return holder.getServiceCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        if (holder.getCountryCode() != null && !holder.getCountryCode().isBlank()) {
            return holder.getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        return "CI";
    }

    private String resolveEmail(UUID accountHolderId) {
        AppUser appUser = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).orElse(null);
        if (appUser == null) {
            return "";
        }
        User user = userRepository.findById(appUser.getId()).orElse(null);
        return user == null ? "" : nullToEmpty(user.getEmail());
    }

    private String displayName(AccountHolder holder) {
        String firstName = holder.getFirstName() == null ? "" : holder.getFirstName().trim();
        String lastName = holder.getLastName() == null ? "" : holder.getLastName().trim();
        return (firstName + " " + lastName).trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private CommerceSettlementStatus resolveSettlementStatus(ShopOrder order) {
        CommerceOrder commerceOrder = commerceOrderRepository.findById(order.getId()).orElse(null);
        if (commerceOrder != null && commerceOrder.getSettlementStatus() != null) {
            return commerceOrder.getSettlementStatus();
        }
        return switch (order.getStatus()) {
            case DELIVERED -> CommerceSettlementStatus.SETTLED;
            case REFUNDED -> CommerceSettlementStatus.REVERSED;
            case CANCELLED -> CommerceSettlementStatus.FAILED;
            default -> CommerceSettlementStatus.PENDING_DELIVERY;
        };
    }

    private String mapDistributionMode(CommerceDistributionMode mode) {
        if (mode == null) {
            return "NONE";
        }
        return switch (mode) {
            case PROMO_CODE -> "PROMO_OWNER";
            case SPONSOR_CHAIN -> "SPONSOR_TREE";
            case NONE -> "NONE";
        };
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : value.setScale(4, RoundingMode.HALF_UP);
    }
}
