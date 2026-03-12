package com.ccapp.shop.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.CheckoutItemRequest;
import com.ccapp.shop.dto.CheckoutRequest;
import com.ccapp.shop.dto.ShopCommissionPoolResponse;
import com.ccapp.shop.dto.ShopOrderItemResponse;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import com.ccapp.shop.repository.ShopCommissionPoolRepository;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopOrderService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MLM_VOLUME_RATE = BigDecimal.valueOf(60);

    private final ShopOrderRepository shopOrderRepository;
    private final ShopOrderItemRepository shopOrderItemRepository;
    private final ShopProductRepository shopProductRepository;
    private final ShopCommissionPoolRepository shopCommissionPoolRepository;
    private final AccountHolderService accountHolderService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public ShopOrderResponse checkout(UUID buyerId, CheckoutRequest request) {
        accountHolderService.getById(buyerId);
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("At least one item is required");
        }

        Map<UUID, ShopProduct> productsById = resolveProducts(request.items());
        CurrencyContext currencyContext = resolveCurrency(productsById);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalPlatformCommission = BigDecimal.ZERO;

        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyerId)
                .status(ShopOrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .currency(currencyContext.currency())
                .paymentMethod(request.paymentMethod().trim().toUpperCase(Locale.ROOT))
                .deliveryAddressId(request.deliveryAddressId())
                .build());

        java.util.ArrayList<ShopOrderItem> items = new java.util.ArrayList<>();
        for (CheckoutItemRequest itemRequest : request.items()) {
            ShopProduct product = productsById.get(itemRequest.productId());
            validateProductActive(product);

            BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            BigDecimal linePlatformCommission = lineAmount
                    .multiply(product.getCommissionPercent())
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);

            BigDecimal lineCvValue = product.getCvValue()
                    .multiply(BigDecimal.valueOf(itemRequest.quantity()))
                    .setScale(4, RoundingMode.HALF_UP);

            totalAmount = totalAmount.add(lineAmount);
            totalPlatformCommission = totalPlatformCommission.add(linePlatformCommission);

            items.add(ShopOrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .quantity(itemRequest.quantity())
                    .unitPrice(product.getPrice())
                    .commissionPercent(product.getCommissionPercent())
                    .cvValue(lineCvValue)
                    .build());
        }

        shopOrderItemRepository.saveAll(items);

        BigDecimal merchantAmount = totalAmount.subtract(totalPlatformCommission).setScale(4, RoundingMode.HALF_UP);
        BigDecimal mlmVolume = totalPlatformCommission
                .multiply(MLM_VOLUME_RATE)
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);

        order.setTotalAmount(totalAmount.setScale(4, RoundingMode.HALF_UP));
        shopOrderRepository.save(order);

        ShopCommissionPool pool = shopCommissionPoolRepository.save(ShopCommissionPool.builder()
                .order(order)
                .merchantAmount(merchantAmount)
                .platformCommission(totalPlatformCommission.setScale(4, RoundingMode.HALF_UP))
                .mlmVolume(mlmVolume)
                .build());
        PlatformMonitoringHooks.onMarketplaceOrderCheckout(items.size(), order.getTotalAmount());
        log.info("marketplace_order_checkout orderId={} buyerId={} items={} totalAmount={} platformCommission={} mlmVolume={}",
                order.getId(), buyerId, items.size(), order.getTotalAmount(), pool.getPlatformCommission(), pool.getMlmVolume());

        return toResponse(order, items, pool);
    }

    @Transactional(readOnly = true)
    public List<ShopOrderResponse> listBuyerOrders(UUID buyerId) {
        accountHolderService.getById(buyerId);
        return shopOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShopOrderResponse getBuyerOrder(UUID buyerId, UUID orderId) {
        ShopOrder order = findOrder(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new NotFoundException("Order not found");
        }
        return toResponse(order);
    }

    @Transactional
    public ShopOrderResponse updateStatus(UUID orderId, ShopOrderStatus targetStatus) {
        ShopOrder order = findOrder(orderId);
        ShopOrderStatus currentStatus = order.getStatus();

        if (currentStatus == targetStatus) {
            return toResponse(order);
        }
        if (!isTransitionAllowed(currentStatus, targetStatus)) {
            throw new BusinessException("Invalid order status transition: " + currentStatus + " -> " + targetStatus);
        }

        order.setStatus(targetStatus);
        ShopOrder saved = shopOrderRepository.save(order);

        if (targetStatus == ShopOrderStatus.DELIVERED) {
            ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(saved.getId())
                    .orElseThrow(() -> new NotFoundException("Commission pool not found for delivered order"));
            PlatformMonitoringHooks.onMarketplaceOrderDelivered(pool.getMlmVolume());
            log.info("marketplace_order_delivered orderId={} buyerId={} merchantAmount={} platformCommission={} mlmVolume={}",
                    saved.getId(), saved.getBuyerId(), pool.getMerchantAmount(), pool.getPlatformCommission(), pool.getMlmVolume());
            domainEventPublisher.publish(ShopOrderDeliveredEvent.create(
                    saved.getId(),
                    saved.getBuyerId(),
                    saved.getTotalAmount(),
                    saved.getCurrency(),
                    pool.getMerchantAmount(),
                    pool.getPlatformCommission(),
                    pool.getMlmVolume()
            ));
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ShopOrder findOrder(UUID orderId) {
        return shopOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    private boolean isTransitionAllowed(ShopOrderStatus from, ShopOrderStatus to) {
        return switch (from) {
            case CREATED -> to == ShopOrderStatus.PAID || to == ShopOrderStatus.CANCELLED;
            case PAID -> to == ShopOrderStatus.SHIPPED || to == ShopOrderStatus.DELIVERED || to == ShopOrderStatus.CANCELLED;
            case SHIPPED -> to == ShopOrderStatus.DELIVERED || to == ShopOrderStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private Map<UUID, ShopProduct> resolveProducts(List<CheckoutItemRequest> itemRequests) {
        List<UUID> productIds = itemRequests.stream()
                .map(CheckoutItemRequest::productId)
                .toList();
        List<UUID> distinctProductIds = productIds.stream().distinct().toList();
        List<ShopProduct> products = shopProductRepository.findAllById(distinctProductIds);
        if (products.size() != distinctProductIds.size()) {
            throw new NotFoundException("One or more products not found");
        }
        Map<UUID, ShopProduct> productsById = new HashMap<>();
        for (ShopProduct product : products) {
            productsById.put(product.getId(), product);
        }
        return productsById;
    }

    private CurrencyContext resolveCurrency(Map<UUID, ShopProduct> productsById) {
        String currency = null;
        for (ShopProduct product : productsById.values()) {
            if (currency == null) {
                currency = product.getCurrency();
                continue;
            }
            if (!currency.equalsIgnoreCase(product.getCurrency())) {
                throw new BusinessException("All products in an order must share the same currency");
            }
        }
        if (currency == null) {
            throw new BusinessException("Unable to resolve order currency");
        }
        return new CurrencyContext(currency.toUpperCase(Locale.ROOT));
    }

    private void validateProductActive(ShopProduct product) {
        if (!product.isActive()) {
            throw new BusinessException("Inactive product cannot be purchased");
        }
    }

    private ShopOrderResponse toResponse(ShopOrder order) {
        List<ShopOrderItem> items = shopOrderItemRepository.findByOrderId(order.getId());
        ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(order.getId())
                .orElse(null);
        return toResponse(order, items, pool);
    }

    private ShopOrderResponse toResponse(ShopOrder order,
                                         List<ShopOrderItem> items,
                                         ShopCommissionPool pool) {
        return new ShopOrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPaymentMethod(),
                order.getDeliveryAddressId(),
                order.getCreatedAt(),
                items.stream().map(this::toItemResponse).toList(),
                pool != null ? new ShopCommissionPoolResponse(
                        pool.getMerchantAmount(),
                        pool.getPlatformCommission(),
                        pool.getMlmVolume()
                ) : null
        );
    }

    private ShopOrderItemResponse toItemResponse(ShopOrderItem item) {
        return new ShopOrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getCommissionPercent(),
                item.getCvValue()
        );
    }

    private record CurrencyContext(String currency) {
    }
}
