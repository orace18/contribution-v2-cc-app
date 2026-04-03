package com.ccapp.commerce.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.commerce.domain.entity.CommerceOrder;
import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.domain.entity.CommercePromoCode;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.commerce.dto.CommerceCheckoutItemRequest;
import com.ccapp.commerce.dto.CommerceCheckoutRequest;
import com.ccapp.commerce.dto.CommerceOrderItemResponse;
import com.ccapp.commerce.dto.CommerceOrderResponse;
import com.ccapp.commerce.dto.CommerceOrderSettlementBeneficiaryResponse;
import com.ccapp.commerce.dto.CommerceOrderSettlementResponse;
import com.ccapp.commerce.dto.CommercePayOrderRequest;
import com.ccapp.commerce.event.PromoCodeAppliedEvent;
import com.ccapp.commerce.repository.CommerceDsbPayoutRepository;
import com.ccapp.commerce.repository.CommerceOrderRepository;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.commerce.repository.CommercePromoCodeRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.CheckoutItemRequest;
import com.ccapp.shop.dto.CheckoutRequest;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.dto.UpdateShopOrderStatusRequest;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommerceOrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final ShopOrderService shopOrderService;
    private final ShopOrderRepository shopOrderRepository;
    private final ShopOrderItemRepository shopOrderItemRepository;
    private final ShopCommissionPoolRepository shopCommissionPoolRepository;
    private final ShopProductRepository shopProductRepository;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CommerceProductRepository commerceProductRepository;
    private final CommerceDsbPayoutRepository commerceDsbPayoutRepository;
    private final CommercePromoCodeRepository commercePromoCodeRepository;
    private final CommercePromoCodeService commercePromoCodeService;
    private final CommerceMerchantService commerceMerchantService;
    private final AccountHolderService accountHolderService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public CommerceOrderResponse checkout(UUID buyerAccountHolderId, CommerceCheckoutRequest request) {
        CheckoutPreparation preparation = prepareCheckout(buyerAccountHolderId, request.items());
        CommercePromoCodeService.ValidatedPromoCode validatedPromoCode = null;
        BigDecimal discountAmount = ZERO;
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            validatedPromoCode = commercePromoCodeService.validateForCheckout(
                    request.promoCode(),
                    preparation.countryCode(),
                    preparation.grossAmount(),
                    preparation.totalDsbAmount()
            );
            discountAmount = validatedPromoCode.discountAmount();
        }

        BigDecimal netPaidAmount = preparation.grossAmount().subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);
        if (netPaidAmount.signum() <= 0) {
            throw new BusinessException("PROMO_CODE_INVALID_FOR_ORDER");
        }

        ShopOrderResponse shopResponse = shopOrderService.checkout(
                buyerAccountHolderId,
                new CheckoutRequest(
                        request.items().stream()
                                .map(item -> new CheckoutItemRequest(item.productId(), item.quantity()))
                                .toList(),
                        request.paymentMethod(),
                        request.deliveryAddressId()
                )
        );

        ShopOrder order = shopOrderRepository.findById(shopResponse.id()).orElseThrow();
        order.setTotalAmount(scale(netPaidAmount));
        shopOrderRepository.save(order);

        commerceOrderRepository.save(CommerceOrder.builder()
                .orderId(order.getId())
                .buyerAccountHolderId(order.getBuyerId())
                .merchantAccountHolderId(preparation.merchantId())
                .countryCode(preparation.countryCode())
                .currency(order.getCurrency())
                .promoCodeId(validatedPromoCode == null ? null : validatedPromoCode.promoCodeId())
                .grossAmount(preparation.grossAmount())
                .discountAmount(scale(discountAmount))
                .netPaidAmount(scale(netPaidAmount))
                .totalDsbAmount(preparation.totalDsbAmount())
                .platformCommission(ZERO)
                .merchantNetAmount(preparation.grossAmount())
                .volumeCv(ZERO)
                .distributionMode(CommerceDistributionMode.NONE)
                .settlementStatus(CommerceSettlementStatus.PENDING_DELIVERY)
                .build());

        if (validatedPromoCode != null) {
            commercePromoCodeService.incrementUsage(validatedPromoCode.promoCodeId());
            domainEventPublisher.publish(PromoCodeAppliedEvent.create(
                    order.getId(),
                    validatedPromoCode.promoCodeId(),
                    validatedPromoCode.code(),
                    buyerAccountHolderId,
                    validatedPromoCode.discountAmount()
            ));
        }

        return buildSingleOrderResponse(
                order,
                shopOrderItemRepository.findByOrderId(order.getId()),
                shopCommissionPoolRepository.findByOrderId(order.getId()).orElse(null),
                commerceOrderRepository.findById(order.getId()).orElse(null),
                validatedPromoCode == null ? null : validatedPromoCode.code(),
                null,
                false,
                null,
                null
        );
    }

    @Transactional
    public CommerceOrderResponse pay(UUID actorUserId,
                                     UUID buyerAccountHolderId,
                                     UUID orderId,
                                     CommercePayOrderRequest request) {
        ShopOrder order = requireBuyerOrder(buyerAccountHolderId, orderId);
        ShopOrderResponse shopResponse = shopOrderService.updateStatus(
                orderId,
                actorUserId,
                new UpdateShopOrderStatusRequest(
                        ShopOrderStatus.PAID,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode()
                )
        );
        ShopOrder updated = shopOrderRepository.findById(orderId).orElseThrow();
        return buildSingleOrderResponse(
                updated,
                shopOrderItemRepository.findByOrderId(orderId),
                shopCommissionPoolRepository.findByOrderId(orderId).orElse(null),
                commerceOrderRepository.findById(orderId).orElse(null),
                resolvePromoCode(orderId),
                shopResponse.paymentOperationId(),
                shopResponse.otpRequired(),
                shopResponse.otpChallengeId(),
                shopResponse.otpExpiresAt()
        );
    }

    @Transactional
    public CommerceOrderResponse confirmDelivery(UUID actorUserId,
                                                 UUID merchantAccountHolderId,
                                                 UUID orderId) {
        ShopOrder order = requireMerchantOrder(merchantAccountHolderId, orderId);
        shopOrderService.updateStatus(
                orderId,
                actorUserId,
                new UpdateShopOrderStatusRequest(ShopOrderStatus.DELIVERED, null, null, null)
        );
        ShopOrder updated = shopOrderRepository.findById(orderId).orElseThrow();
        return buildSingleOrderResponse(
                updated,
                shopOrderItemRepository.findByOrderId(orderId),
                shopCommissionPoolRepository.findByOrderId(orderId).orElse(null),
                commerceOrderRepository.findById(orderId).orElse(null),
                resolvePromoCode(orderId),
                null,
                false,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<CommerceOrderResponse> listBuyerOrders(UUID buyerAccountHolderId) {
        accountHolderService.getById(buyerAccountHolderId);
        List<ShopOrder> orders = shopOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerAccountHolderId);
        return mapOrders(orders);
    }

    @Transactional(readOnly = true)
    public CommerceOrderResponse getBuyerOrder(UUID buyerAccountHolderId, UUID orderId) {
        ShopOrder order = requireBuyerOrder(buyerAccountHolderId, orderId);
        return mapOrders(List.of(order)).stream().findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public CommerceOrderSettlementResponse getBuyerSettlement(UUID buyerAccountHolderId, UUID orderId) {
        ShopOrder order = requireBuyerOrder(buyerAccountHolderId, orderId);
        return buildSettlementResponse(order);
    }

    @Transactional(readOnly = true)
    public List<CommerceOrderSettlementResponse> listMerchantSettlements(UUID merchantAccountHolderId) {
        accountHolderService.getById(merchantAccountHolderId);
        return shopOrderRepository.findByMerchantIdOrderByCreatedAtDesc(merchantAccountHolderId).stream()
                .filter(order -> order.getStatus() == ShopOrderStatus.DELIVERED || order.getStatus() == ShopOrderStatus.REFUNDED)
                .map(this::buildSettlementResponse)
                .toList();
    }

    private List<CommerceOrderResponse> mapOrders(List<ShopOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = orders.stream().map(ShopOrder::getId).toList();
        Map<UUID, List<ShopOrderItem>> itemsByOrderId = shopOrderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        Map<UUID, ShopCommissionPool> poolsByOrderId = shopCommissionPoolRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(pool -> pool.getOrder().getId(), pool -> pool));
        Map<UUID, CommerceOrder> commerceOrdersById = commerceOrderRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(CommerceOrder::getOrderId, commerceOrder -> commerceOrder));

        Map<UUID, String> promoCodesById = loadPromoCodes(commerceOrdersById.values().stream()
                .map(CommerceOrder::getPromoCodeId)
                .filter(id -> id != null)
                .distinct()
                .toList());

        List<UUID> merchantIds = orders.stream()
                .map(ShopOrder::getMerchantId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, String> merchantNames = commerceMerchantService.resolveDisplayNames(merchantIds);

        Map<UUID, ShopProduct> productsById = loadProducts(itemsByOrderId.values().stream()
                .flatMap(List::stream)
                .map(ShopOrderItem::getProductId)
                .distinct()
                .toList());
        Map<UUID, CommerceProduct> commerceProductsById = loadCommerceProducts(new ArrayList<>(productsById.keySet()));

        return orders.stream()
                .sorted(Comparator.comparing(ShopOrder::getCreatedAt).reversed())
                .map(order -> buildOrderResponse(
                        order,
                        itemsByOrderId.getOrDefault(order.getId(), List.of()),
                        poolsByOrderId.get(order.getId()),
                        commerceOrdersById.get(order.getId()),
                        promoCodesById,
                        merchantNames,
                        productsById,
                        commerceProductsById,
                        null,
                        false,
                        null,
                        null
                ))
                .toList();
    }

    private CommerceOrderResponse buildSingleOrderResponse(ShopOrder order,
                                                           List<ShopOrderItem> items,
                                                           ShopCommissionPool pool,
                                                           CommerceOrder commerceOrder,
                                                           String promoCode,
                                                           UUID paymentOperationId,
                                                           boolean otpRequired,
                                                           UUID otpChallengeId,
                                                           OffsetDateTime otpExpiresAt) {
        Map<UUID, ShopProduct> productsById = loadProducts(items.stream().map(ShopOrderItem::getProductId).distinct().toList());
        Map<UUID, CommerceProduct> commerceProductsById = loadCommerceProducts(new ArrayList<>(productsById.keySet()));
        Map<UUID, String> merchantNames = commerceMerchantService.resolveDisplayNames(
                order.getMerchantId() == null ? List.of() : List.of(order.getMerchantId())
        );
        Map<UUID, String> promoCodes = new HashMap<>();
        if (promoCode != null && commerceOrder != null && commerceOrder.getPromoCodeId() != null) {
            promoCodes.put(commerceOrder.getPromoCodeId(), promoCode);
        } else if (commerceOrder != null && commerceOrder.getPromoCodeId() != null) {
            promoCodes.putAll(loadPromoCodes(List.of(commerceOrder.getPromoCodeId())));
        }
        return buildOrderResponse(
                order,
                items,
                pool,
                commerceOrder,
                promoCodes,
                merchantNames,
                productsById,
                commerceProductsById,
                paymentOperationId,
                otpRequired,
                otpChallengeId,
                otpExpiresAt
        );
    }

    private CommerceOrderResponse buildOrderResponse(ShopOrder order,
                                                     List<ShopOrderItem> items,
                                                     ShopCommissionPool pool,
                                                     CommerceOrder commerceOrder,
                                                     Map<UUID, String> promoCodesById,
                                                     Map<UUID, String> merchantNames,
                                                     Map<UUID, ShopProduct> productsById,
                                                     Map<UUID, CommerceProduct> commerceProductsById,
                                                     UUID paymentOperationId,
                                                     boolean otpRequired,
                                                     UUID otpChallengeId,
                                                     OffsetDateTime otpExpiresAt) {
        CommerceOrder effectiveOrder = commerceOrder != null
                ? commerceOrder
                : buildFallbackOrder(order, items, pool, commerceProductsById);
        String promoCode = effectiveOrder.getPromoCodeId() == null
                ? null
                : promoCodesById.get(effectiveOrder.getPromoCodeId());

        List<CommerceOrderItemResponse> itemResponses = items.stream()
                .map(item -> {
                    ShopProduct product = productsById.get(item.getProductId());
                    CommerceProduct commerceProduct = commerceProductsById.get(item.getProductId());
                    BigDecimal lineDsb = commerceProduct == null
                            ? ZERO
                            : scale(commerceProduct.getDsbAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
                    return new CommerceOrderItemResponse(
                            item.getProductId(),
                            product == null ? null : product.getName(),
                            item.getQuantity(),
                            scale(item.getUnitPrice()),
                            item.getCommissionPercent() == null ? null : item.getCommissionPercent().setScale(2, RoundingMode.HALF_UP),
                            scale(item.getCvValue()),
                            lineDsb
                    );
                })
                .toList();

        return new CommerceOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getBuyerId(),
                order.getMerchantId(),
                merchantNames.get(order.getMerchantId()),
                scale(effectiveOrder.getGrossAmount()),
                scale(effectiveOrder.getDiscountAmount()),
                scale(effectiveOrder.getNetPaidAmount()),
                scale(effectiveOrder.getTotalDsbAmount()),
                scale(effectiveOrder.getPlatformCommission()),
                scale(effectiveOrder.getMerchantNetAmount()),
                scale(effectiveOrder.getVolumeCv()),
                order.getCurrency(),
                order.getPaymentMethod(),
                order.getDeliveryAddressId(),
                effectiveOrder.getPromoCodeId() != null,
                promoCode,
                effectiveOrder.getDistributionMode(),
                effectiveOrder.getSettlementStatus(),
                order.getCreatedAt(),
                order.getDeliveredAt(),
                effectiveOrder.getSettledAt(),
                itemResponses,
                paymentOperationId,
                otpRequired,
                otpChallengeId,
                otpExpiresAt
        );
    }

    private CommerceOrderSettlementResponse buildSettlementResponse(ShopOrder order) {
        List<ShopOrderItem> items = shopOrderItemRepository.findByOrderId(order.getId());
        ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(order.getId()).orElse(null);
        Map<UUID, CommerceProduct> commerceProductsById = loadCommerceProducts(
                items.stream().map(ShopOrderItem::getProductId).distinct().toList()
        );
        CommerceOrder commerceOrder = commerceOrderRepository.findById(order.getId()).orElse(null);
        CommerceOrder effectiveOrder = commerceOrder != null
                ? commerceOrder
                : buildFallbackOrder(order, items, pool, commerceProductsById);

        return new CommerceOrderSettlementResponse(
                order.getId(),
                order.getStatus(),
                effectiveOrder.getSettlementStatus(),
                scale(effectiveOrder.getGrossAmount()),
                scale(effectiveOrder.getDiscountAmount()),
                scale(effectiveOrder.getNetPaidAmount()),
                scale(effectiveOrder.getPlatformCommission()),
                scale(effectiveOrder.getTotalDsbAmount()),
                scale(effectiveOrder.getMerchantNetAmount()),
                scale(effectiveOrder.getVolumeCv()),
                effectiveOrder.getDistributionMode(),
                order.getDeliveredAt(),
                effectiveOrder.getSettledAt(),
                commerceDsbPayoutRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                        .map(payout -> new CommerceOrderSettlementBeneficiaryResponse(
                                payout.getBeneficiaryAccountHolderId(),
                                payout.getRole().name(),
                                scale(payout.getAmount()),
                                payout.getCurrency()
                        ))
                        .toList()
        );
    }

    private CommerceOrder buildFallbackOrder(ShopOrder order,
                                             List<ShopOrderItem> items,
                                             ShopCommissionPool pool,
                                             Map<UUID, CommerceProduct> commerceProductsById) {
        BigDecimal grossAmount = items.stream()
                .map(item -> scale(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity())).setScale(4, RoundingMode.HALF_UP))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalDsbAmount = items.stream()
                .map(item -> {
                    CommerceProduct commerceProduct = commerceProductsById.get(item.getProductId());
                    if (commerceProduct == null || commerceProduct.getDsbAmount() == null) {
                        return ZERO;
                    }
                    return scale(commerceProduct.getDsbAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
                })
                .reduce(ZERO, BigDecimal::add);

        return CommerceOrder.builder()
                .orderId(order.getId())
                .buyerAccountHolderId(order.getBuyerId())
                .merchantAccountHolderId(order.getMerchantId())
                .countryCode(order.getCountryCode())
                .currency(order.getCurrency())
                .grossAmount(scale(grossAmount))
                .discountAmount(ZERO)
                .netPaidAmount(scale(order.getTotalAmount()))
                .totalDsbAmount(scale(totalDsbAmount))
                .platformCommission(pool == null ? ZERO : scale(pool.getPlatformCommission()))
                .merchantNetAmount(pool == null ? scale(grossAmount) : scale(pool.getMerchantAmount()))
                .volumeCv(pool == null ? ZERO : scale(pool.getMlmVolume()))
                .distributionMode(CommerceDistributionMode.NONE)
                .settlementStatus(order.getStatus() == ShopOrderStatus.REFUNDED
                        ? CommerceSettlementStatus.REVERSED
                        : (order.getStatus() == ShopOrderStatus.DELIVERED
                        ? CommerceSettlementStatus.SETTLED
                        : CommerceSettlementStatus.PENDING_DELIVERY))
                .settledAt(order.getDeliveredAt())
                .build();
    }

    private CheckoutPreparation prepareCheckout(UUID buyerAccountHolderId, List<CommerceCheckoutItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("At least one item is required");
        }
        Map<UUID, ShopProduct> productsById = loadProducts(
                items.stream().map(CommerceCheckoutItemRequest::productId).distinct().toList()
        );
        if (productsById.size() != items.stream().map(CommerceCheckoutItemRequest::productId).distinct().count()) {
            throw new NotFoundException("One or more products not found");
        }

        String currency = null;
        UUID merchantId = null;
        BigDecimal grossAmount = ZERO;
        BigDecimal totalDsbAmount = ZERO;

        Map<UUID, CommerceProduct> commerceProductsById = loadCommerceProducts(new ArrayList<>(productsById.keySet()));
        for (CommerceCheckoutItemRequest item : items) {
            ShopProduct product = productsById.get(item.productId());
            if (product == null) {
                throw new NotFoundException("One or more products not found");
            }
            if (!product.isActive()) {
                throw new BusinessException("Inactive product cannot be purchased");
            }
            if (currency == null) {
                currency = product.getCurrency();
                merchantId = product.getMerchantId();
            } else {
                if (!currency.equalsIgnoreCase(product.getCurrency())) {
                    throw new BusinessException("All products in an order must share the same currency");
                }
                if (!merchantId.equals(product.getMerchantId())) {
                    throw new BusinessException("All products in an order must belong to the same merchant");
                }
            }
            grossAmount = grossAmount.add(
                    scale(product.getPrice()).multiply(BigDecimal.valueOf(item.quantity())).setScale(4, RoundingMode.HALF_UP)
            );
            CommerceProduct commerceProduct = commerceProductsById.get(item.productId());
            if (commerceProduct != null && commerceProduct.getDsbAmount() != null) {
                totalDsbAmount = totalDsbAmount.add(
                        scale(commerceProduct.getDsbAmount()).multiply(BigDecimal.valueOf(item.quantity())).setScale(4, RoundingMode.HALF_UP)
                );
            }
        }

        AccountHolder buyer = accountHolderService.getById(buyerAccountHolderId);
        AccountHolder merchant = accountHolderService.getById(merchantId);
        return new CheckoutPreparation(
                merchantId,
                resolveCountryCode(merchant, buyer),
                scale(grossAmount),
                scale(totalDsbAmount)
        );
    }

    private String resolvePromoCode(UUID orderId) {
        CommerceOrder commerceOrder = commerceOrderRepository.findById(orderId).orElse(null);
        if (commerceOrder == null || commerceOrder.getPromoCodeId() == null) {
            return null;
        }
        return commercePromoCodeService.findCode(commerceOrder.getPromoCodeId());
    }

    private ShopOrder requireBuyerOrder(UUID buyerAccountHolderId, UUID orderId) {
        ShopOrder order = shopOrderService.findOrder(orderId);
        if (!order.getBuyerId().equals(buyerAccountHolderId)) {
            throw new NotFoundException("Order not found");
        }
        return order;
    }

    private ShopOrder requireMerchantOrder(UUID merchantAccountHolderId, UUID orderId) {
        ShopOrder order = shopOrderService.findOrder(orderId);
        if (order.getMerchantId() == null || !order.getMerchantId().equals(merchantAccountHolderId)) {
            throw new NotFoundException("Order not found");
        }
        return order;
    }

    private Map<UUID, String> loadPromoCodes(List<UUID> promoCodeIds) {
        Map<UUID, String> codesById = new HashMap<>();
        if (promoCodeIds == null || promoCodeIds.isEmpty()) {
            return codesById;
        }
        commercePromoCodeRepository.findAllById(promoCodeIds)
                .forEach(promoCode -> codesById.put(promoCode.getId(), promoCode.getCode()));
        return codesById;
    }

    private Map<UUID, ShopProduct> loadProducts(List<UUID> productIds) {
        Map<UUID, ShopProduct> productsById = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return productsById;
        }
        shopProductRepository.findAllById(productIds)
                .forEach(product -> productsById.put(product.getId(), product));
        return productsById;
    }

    private Map<UUID, CommerceProduct> loadCommerceProducts(List<UUID> productIds) {
        Map<UUID, CommerceProduct> productsById = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return productsById;
        }
        commerceProductRepository.findByProductIdIn(productIds)
                .forEach(product -> productsById.put(product.getProductId(), product));
        return productsById;
    }

    private String resolveCountryCode(AccountHolder preferred, AccountHolder fallback) {
        for (AccountHolder candidate : List.of(preferred, fallback)) {
            if (candidate == null) {
                continue;
            }
            if (candidate.getServiceCountryCode() != null && !candidate.getServiceCountryCode().isBlank()) {
                return candidate.getServiceCountryCode().trim().toUpperCase(Locale.ROOT);
            }
            if (candidate.getCountryCode() != null && !candidate.getCountryCode().isBlank()) {
                return candidate.getCountryCode().trim().toUpperCase(Locale.ROOT);
            }
        }
        return "INT";
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    private record CheckoutPreparation(
            UUID merchantId,
            String countryCode,
            BigDecimal grossAmount,
            BigDecimal totalDsbAmount
    ) {
    }
}
