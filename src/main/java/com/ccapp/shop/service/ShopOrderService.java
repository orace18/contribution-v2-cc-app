package com.ccapp.shop.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.shop.domain.entity.ShopCommissionPool;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.CheckoutItemRequest;
import com.ccapp.shop.dto.CheckoutRequest;
import com.ccapp.shop.dto.RefundShopOrderRequest;
import com.ccapp.shop.dto.ShopCommissionPoolResponse;
import com.ccapp.shop.dto.ShopOrderItemResponse;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.event.MarketplaceCommissionAllocatedEvent;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import com.ccapp.shop.event.ShopOrderPaidEvent;
import com.ccapp.shop.event.ShopOrderRefundedEvent;
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
import java.time.OffsetDateTime;
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
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_TR_MIN = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_TAUX_CV = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
    private static final String WALLET_PAYMENT_METHOD = "WALLET";

    private final ShopOrderRepository shopOrderRepository;
    private final ShopOrderItemRepository shopOrderItemRepository;
    private final ShopProductRepository shopProductRepository;
    private final ShopCommissionPoolRepository shopCommissionPoolRepository;
    private final AccountHolderService accountHolderService;
    private final CountryConfigResolver countryConfigResolver;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public ShopOrderResponse checkout(UUID buyerId, CheckoutRequest request) {
        AccountHolder buyer = accountHolderService.getById(buyerId);
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("At least one item is required");
        }

        Map<UUID, ShopProduct> productsById = resolveProducts(request.items());
        OrderContext orderContext = resolveOrderContext(productsById, buyer);
        MarketplaceRateProfile rateProfile = resolveMarketplaceRateProfile(orderContext.countryCode());

        BigDecimal totalAmount = BigDecimal.ZERO;
        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyerId)
                .merchantId(orderContext.merchantId())
                .countryCode(orderContext.countryCode())
                .status(ShopOrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .currency(orderContext.currency())
                .paymentMethod(request.paymentMethod().trim().toUpperCase(Locale.ROOT))
                .deliveryAddressId(request.deliveryAddressId())
                .build());

        java.util.ArrayList<ShopOrderItem> items = new java.util.ArrayList<>();
        for (CheckoutItemRequest itemRequest : request.items()) {
            ShopProduct product = productsById.get(itemRequest.productId());
            validateProductActive(product);

            BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            BigDecimal effectiveCommissionRate = resolveEffectiveCommissionRate(product.getCommissionPercent(), rateProfile);
            BigDecimal lineCvValue = computeDisplayCv(lineAmount, effectiveCommissionRate, product.getCvValue(), rateProfile.tauxCv());

            totalAmount = totalAmount.add(lineAmount);
            items.add(ShopOrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .quantity(itemRequest.quantity())
                    .unitPrice(product.getPrice())
                    .commissionPercent(effectiveCommissionRate)
                    .cvValue(lineCvValue)
                    .build());
        }

        shopOrderItemRepository.saveAll(items);
        order.setTotalAmount(scale(totalAmount));
        shopOrderRepository.save(order);

        ShopCommissionPool pool = shopCommissionPoolRepository.save(ShopCommissionPool.builder()
                .order(order)
                .merchantAmount(ZERO)
                .platformCommission(ZERO)
                .mlmVolume(ZERO)
                .build());
        PlatformMonitoringHooks.onMarketplaceOrderCheckout(items.size(), order.getTotalAmount());
        log.info("marketplace_order_checkout orderId={} buyerId={} merchantId={} countryCode={} items={} totalAmount={}",
                order.getId(), buyerId, orderContext.merchantId(), orderContext.countryCode(), items.size(), order.getTotalAmount());

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

        if (targetStatus == ShopOrderStatus.PAID && order.getPaidAt() == null) {
            order.setPaidAt(OffsetDateTime.now());
        }
        if (targetStatus == ShopOrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(OffsetDateTime.now());
        }
        order.setStatus(targetStatus);
        ShopOrder saved = shopOrderRepository.save(order);

        if (targetStatus == ShopOrderStatus.PAID) {
            recordMarketplacePayment(saved);
            domainEventPublisher.publish(ShopOrderPaidEvent.create(
                    saved.getId(),
                    saved.getBuyerId(),
                    resolveMerchantId(saved, shopOrderItemRepository.findByOrderId(saved.getId())),
                    resolveOrderCountryCode(saved),
                    saved.getTotalAmount(),
                    saved.getCurrency(),
                    saved.getPaymentMethod()
            ));
        }

        if (targetStatus == ShopOrderStatus.DELIVERED) {
            recordMarketplacePayment(saved);
            MarketplaceSettlement settlement = settleDeliveredOrder(saved);
            PlatformMonitoringHooks.onMarketplaceOrderDelivered(settlement.businessVolume());
            log.info("marketplace_order_delivered orderId={} buyerId={} merchantId={} merchantAmount={} commission={} businessVolume={}",
                    saved.getId(), saved.getBuyerId(), resolveMerchantId(saved, shopOrderItemRepository.findByOrderId(saved.getId())),
                    settlement.merchantNetAmount(), settlement.commissionAmount(), settlement.businessVolume());
            domainEventPublisher.publish(MarketplaceCommissionAllocatedEvent.create(
                    saved.getId(),
                    saved.getBuyerId(),
                    saved.getMerchantId(),
                    resolveOrderCountryCode(saved),
                    settlement.commissionAmount(),
                    saved.getCurrency()
            ));
            domainEventPublisher.publish(ShopOrderDeliveredEvent.create(
                    saved.getId(),
                    saved.getBuyerId(),
                    saved.getTotalAmount(),
                    saved.getCurrency(),
                    settlement.merchantNetAmount(),
                    settlement.commissionAmount(),
                    settlement.businessVolume()
            ));
        }

        return toResponse(saved);
    }

    @Transactional
    public ShopOrderResponse refundDeliveredOrder(UUID orderId, RefundShopOrderRequest request) {
        ShopOrder order = findOrder(orderId);
        if (order.getStatus() != ShopOrderStatus.DELIVERED) {
            throw new BusinessException("Only delivered orders can be refunded");
        }
        ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new NotFoundException("Commission pool not found for refunded order"));

        order.setStatus(ShopOrderStatus.REFUNDED);
        order.setRefundedAt(OffsetDateTime.now());
        ShopOrder saved = shopOrderRepository.save(order);
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "N/A"
                : request.reason().trim();
        domainEventPublisher.publish(ShopOrderRefundedEvent.create(
                saved.getId(),
                saved.getBuyerId(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                pool.getMerchantAmount(),
                pool.getPlatformCommission(),
                pool.getMlmVolume(),
                reason
        ));
        log.info("marketplace_order_refunded orderId={} buyerId={} reason={} merchantAmount={} commission={} businessVolume={}",
                saved.getId(), saved.getBuyerId(), reason, pool.getMerchantAmount(), pool.getPlatformCommission(), pool.getMlmVolume());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ShopOrder findOrder(UUID orderId) {
        return shopOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    private void recordMarketplacePayment(ShopOrder order) {
        Operation initiated = operationService.createInitiatedOperation(
                order.getBuyerId(),
                order.getBuyerId(),
                OperationType.MARKETPLACE_PAYMENT,
                scale(order.getTotalAmount()),
                order.getCurrency(),
                "marketplace-payment:" + order.getId(),
                "Marketplace payment for order " + order.getId()
        );
        Operation confirmed = initiated.getStatus() == OperationStatus.CONFIRMED
                ? initiated
                : operationService.confirmOperation(initiated.getId());
        accountingPostingService.postMarketplacePayment(
                confirmed,
                order.getBuyerId(),
                resolveOrderCountryCode(order),
                isWalletPaymentMethod(order.getPaymentMethod())
        );
    }

    private MarketplaceSettlement settleDeliveredOrder(ShopOrder order) {
        List<ShopOrderItem> items = shopOrderItemRepository.findByOrderId(order.getId());
        if (items.isEmpty()) {
            throw new BusinessException("Delivered order must contain items");
        }

        UUID merchantId = resolveMerchantId(order, items);
        String countryCode = resolveOrderCountryCode(order);
        BigDecimal totalAmount = ZERO;
        BigDecimal commissionAmount = ZERO;
        for (ShopOrderItem item : items) {
            BigDecimal lineAmount = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineCommission = lineAmount
                    .multiply(item.getCommissionPercent())
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineAmount).setScale(4, RoundingMode.HALF_UP);
            commissionAmount = commissionAmount.add(lineCommission).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal merchantNetAmount = totalAmount.subtract(commissionAmount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal businessVolume = commissionAmount;

        ShopCommissionPool pool = shopCommissionPoolRepository.findByOrderId(order.getId())
                .orElseGet(() -> ShopCommissionPool.builder().order(order).build());
        pool.setMerchantAmount(merchantNetAmount);
        pool.setPlatformCommission(commissionAmount);
        pool.setMlmVolume(businessVolume);
        shopCommissionPoolRepository.save(pool);

        if (merchantNetAmount.signum() > 0) {
            Operation payout = operationService.createInitiatedOperation(
                    merchantId,
                    order.getBuyerId(),
                    OperationType.MARKETPLACE_MERCHANT_PAYOUT,
                    merchantNetAmount,
                    order.getCurrency(),
                    "marketplace-merchant-payout:" + order.getId(),
                    "Marketplace merchant payout for order " + order.getId()
            );
            Operation confirmedPayout = payout.getStatus() == OperationStatus.CONFIRMED
                    ? payout
                    : operationService.confirmOperation(payout.getId());
            accountingPostingService.postMarketplaceMerchantPayout(confirmedPayout, merchantId, countryCode);
        }

        if (commissionAmount.signum() > 0) {
            Operation allocation = operationService.createInitiatedOperation(
                    order.getBuyerId(),
                    order.getBuyerId(),
                    OperationType.MARKETPLACE_COMMISSION_ALLOCATION,
                    commissionAmount,
                    order.getCurrency(),
                    "marketplace-commission-allocation:" + order.getId(),
                    "Marketplace commission allocation for order " + order.getId()
            );
            Operation confirmedAllocation = allocation.getStatus() == OperationStatus.CONFIRMED
                    ? allocation
                    : operationService.confirmOperation(allocation.getId());
            accountingPostingService.postMarketplaceCommissionAllocation(confirmedAllocation, countryCode);
        }

        return new MarketplaceSettlement(merchantNetAmount, commissionAmount, businessVolume);
    }

    private boolean isTransitionAllowed(ShopOrderStatus from, ShopOrderStatus to) {
        return switch (from) {
            case CREATED -> to == ShopOrderStatus.PAID || to == ShopOrderStatus.CANCELLED;
            case PAID -> to == ShopOrderStatus.SHIPPED || to == ShopOrderStatus.DELIVERED || to == ShopOrderStatus.CANCELLED;
            case SHIPPED -> to == ShopOrderStatus.DELIVERED || to == ShopOrderStatus.CANCELLED;
            case DELIVERED, CANCELLED, REFUNDED -> false;
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

    private OrderContext resolveOrderContext(Map<UUID, ShopProduct> productsById, AccountHolder buyer) {
        String currency = null;
        UUID merchantId = null;
        for (ShopProduct product : productsById.values()) {
            if (currency == null) {
                currency = product.getCurrency();
                merchantId = product.getMerchantId();
                continue;
            }
            if (!currency.equalsIgnoreCase(product.getCurrency())) {
                throw new BusinessException("All products in an order must share the same currency");
            }
            if (!merchantId.equals(product.getMerchantId())) {
                throw new BusinessException("All products in an order must belong to the same merchant");
            }
        }
        if (currency == null || merchantId == null) {
            throw new BusinessException("Unable to resolve order marketplace context");
        }
        AccountHolder merchant = accountHolderService.getById(merchantId);
        return new OrderContext(
                currency.toUpperCase(Locale.ROOT),
                merchantId,
                readCountryCode(merchant, buyer)
        );
    }

    private MarketplaceRateProfile resolveMarketplaceRateProfile(String countryCode) {
        var countryGeneral = countryConfigResolver.resolveActiveConfig(countryCode, CountryConfigType.GENERAL);
        var countryPayload = countryConfigResolver.payloadAsObjectNode(countryGeneral);
        var platformGeneral = countryConfigResolver.resolveActivePlatformConfig(PlatformConfigType.GLOBAL_GENERAL);
        var platformPayload = countryConfigResolver.payloadAsObjectNode(platformGeneral);

        BigDecimal countryTrMin = readDecimal(countryPayload, "trMin", "TR_min", "marketplaceDefaultCommission");
        BigDecimal platformTrMin = readDecimal(platformPayload, "trMin", "TR_min", "marketplaceDefaultCommission");
        BigDecimal tauxCv = firstPositive(
                readDecimal(countryPayload, "tauxCv", "Taux_CV"),
                readDecimal(platformPayload, "tauxCv", "Taux_CV"),
                DEFAULT_TAUX_CV
        );

        return new MarketplaceRateProfile(
                firstPositive(countryTrMin, DEFAULT_TR_MIN).setScale(2, RoundingMode.HALF_UP),
                firstPositive(platformTrMin, DEFAULT_TR_MIN).setScale(2, RoundingMode.HALF_UP),
                tauxCv.setScale(4, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal resolveEffectiveCommissionRate(BigDecimal productRate, MarketplaceRateProfile rateProfile) {
        BigDecimal normalizedProductRate = productRate == null
                ? DEFAULT_TR_MIN
                : productRate.setScale(2, RoundingMode.HALF_UP);
        return normalizedProductRate
                .max(rateProfile.countryTrMin())
                .max(rateProfile.platformTrMin())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeDisplayCv(BigDecimal lineAmount,
                                        BigDecimal effectiveCommissionRate,
                                        BigDecimal cvFactor,
                                        BigDecimal tauxCv) {
        if (lineAmount == null || lineAmount.signum() <= 0 || cvFactor == null || cvFactor.signum() <= 0) {
            return ZERO;
        }
        BigDecimal normalizedTauxCv = (tauxCv == null || tauxCv.signum() <= 0)
                ? DEFAULT_TAUX_CV
                : tauxCv;
        return lineAmount
                .multiply(effectiveCommissionRate)
                .divide(HUNDRED, 8, RoundingMode.HALF_UP)
                .multiply(cvFactor)
                .divide(normalizedTauxCv, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal readDecimal(com.fasterxml.jackson.databind.node.ObjectNode payload, String... aliases) {
        if (payload == null) {
            return null;
        }
        for (String alias : aliases) {
            var node = payload.get(alias);
            if (node != null && node.isNumber()) {
                return node.decimalValue();
            }
        }
        return null;
    }

    private BigDecimal firstPositive(BigDecimal primary, BigDecimal fallback) {
        if (primary != null && primary.signum() > 0) {
            return primary;
        }
        return fallback;
    }

    private BigDecimal firstPositive(BigDecimal first, BigDecimal second, BigDecimal fallback) {
        if (first != null && first.signum() > 0) {
            return first;
        }
        if (second != null && second.signum() > 0) {
            return second;
        }
        return fallback;
    }

    private void validateProductActive(ShopProduct product) {
        if (!product.isActive()) {
            throw new BusinessException("Inactive product cannot be purchased");
        }
    }

    private String resolveOrderCountryCode(ShopOrder order) {
        if (order.getCountryCode() != null && !order.getCountryCode().isBlank()) {
            return order.getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        AccountHolder buyer = accountHolderService.getById(order.getBuyerId());
        String countryCode = readCountryCode(null, buyer);
        order.setCountryCode(countryCode);
        shopOrderRepository.save(order);
        return countryCode;
    }

    private UUID resolveMerchantId(ShopOrder order, List<ShopOrderItem> items) {
        if (order.getMerchantId() != null) {
            return order.getMerchantId();
        }
        if (items.isEmpty()) {
            throw new BusinessException("Unable to resolve merchant for empty order");
        }
        UUID productId = items.getFirst().getProductId();
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        order.setMerchantId(product.getMerchantId());
        shopOrderRepository.save(order);
        return product.getMerchantId();
    }

    private String readCountryCode(AccountHolder preferred, AccountHolder fallback) {
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

    private boolean isWalletPaymentMethod(String paymentMethod) {
        return paymentMethod != null && WALLET_PAYMENT_METHOD.equalsIgnoreCase(paymentMethod.trim());
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

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private record OrderContext(String currency, UUID merchantId, String countryCode) {
    }

    private record MarketplaceRateProfile(BigDecimal countryTrMin,
                                          BigDecimal platformTrMin,
                                          BigDecimal tauxCv) {
    }

    private record MarketplaceSettlement(BigDecimal merchantNetAmount,
                                         BigDecimal commissionAmount,
                                         BigDecimal businessVolume) {
    }
}
