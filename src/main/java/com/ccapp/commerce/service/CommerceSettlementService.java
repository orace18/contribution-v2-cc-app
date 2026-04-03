package com.ccapp.commerce.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.commerce.domain.entity.CommerceOrder;
import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.commerce.repository.CommerceOrderRepository;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceSettlementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final CommerceOrderRepository commerceOrderRepository;
    private final CommerceProductRepository commerceProductRepository;
    private final DSBService dsbService;

    @Transactional
    public CommerceSettlementAdjustment applyDeliveredOrderSettlement(ShopOrder order,
                                                                      List<ShopOrderItem> items,
                                                                      BigDecimal grossAmount,
                                                                      BigDecimal platformCommission,
                                                                      BigDecimal volumeCv,
                                                                      String countryCode) {
        CommerceOrder commerceOrder = resolveOrCreate(order, items, grossAmount, countryCode);
        if (commerceOrder.getSettlementStatus() == CommerceSettlementStatus.SETTLED) {
            return new CommerceSettlementAdjustment(
                    scale(commerceOrder.getMerchantNetAmount()),
                    scale(commerceOrder.getTotalDsbAmount()),
                    commerceOrder.getDistributionMode()
            );
        }

        BigDecimal totalDsbAmount = resolveTotalDsb(items);
        BigDecimal merchantNetAmount = scale(grossAmount)
                .subtract(scale(platformCommission))
                .subtract(totalDsbAmount)
                .setScale(4, RoundingMode.HALF_UP);
        if (merchantNetAmount.signum() < 0) {
            commerceOrder.setSettlementStatus(CommerceSettlementStatus.FAILED);
            commerceOrderRepository.save(commerceOrder);
            throw new BusinessException("COMMERCE_SETTLEMENT_NEGATIVE_MERCHANT_NET");
        }

        DSBService.DistributionResult distribution = dsbService.distribute(order, commerceOrder, totalDsbAmount, countryCode);

        commerceOrder.setCountryCode(countryCode);
        commerceOrder.setCurrency(order.getCurrency());
        commerceOrder.setTotalDsbAmount(totalDsbAmount);
        commerceOrder.setPlatformCommission(scale(platformCommission));
        commerceOrder.setMerchantNetAmount(merchantNetAmount);
        commerceOrder.setVolumeCv(scale(volumeCv));
        commerceOrder.setDistributionMode(distribution.distributionMode());
        commerceOrder.setSettlementStatus(CommerceSettlementStatus.SETTLED);
        commerceOrder.setDeliveryConfirmedAt(order.getDeliveredAt() != null ? order.getDeliveredAt() : OffsetDateTime.now());
        commerceOrder.setSettledAt(OffsetDateTime.now());
        commerceOrderRepository.save(commerceOrder);

        return new CommerceSettlementAdjustment(
                merchantNetAmount,
                totalDsbAmount,
                distribution.distributionMode()
        );
    }

    @Transactional
    public void reverseRefundedOrder(UUID orderId) {
        if (orderId == null) {
            return;
        }
        CommerceOrder commerceOrder = commerceOrderRepository.findById(orderId).orElse(null);
        if (commerceOrder == null) {
            return;
        }
        dsbService.reverse(commerceOrder);
        commerceOrder.setSettlementStatus(CommerceSettlementStatus.REVERSED);
        commerceOrder.setSettledAt(OffsetDateTime.now());
        commerceOrderRepository.save(commerceOrder);
    }

    @Transactional(readOnly = true)
    public CommerceOrder findByOrderId(UUID orderId) {
        return commerceOrderRepository.findById(orderId).orElse(null);
    }

    private CommerceOrder resolveOrCreate(ShopOrder order,
                                          List<ShopOrderItem> items,
                                          BigDecimal grossAmount,
                                          String countryCode) {
        return commerceOrderRepository.findById(order.getId())
                .orElseGet(() -> commerceOrderRepository.save(CommerceOrder.builder()
                        .orderId(order.getId())
                        .buyerAccountHolderId(order.getBuyerId())
                        .merchantAccountHolderId(order.getMerchantId())
                        .countryCode(countryCode)
                        .currency(order.getCurrency())
                        .grossAmount(scale(grossAmount))
                        .discountAmount(ZERO)
                        .netPaidAmount(scale(order.getTotalAmount()))
                        .totalDsbAmount(resolveTotalDsb(items))
                        .platformCommission(ZERO)
                        .merchantNetAmount(scale(grossAmount))
                        .volumeCv(ZERO)
                        .distributionMode(CommerceDistributionMode.NONE)
                        .settlementStatus(CommerceSettlementStatus.PENDING_DELIVERY)
                        .build()));
    }

    private BigDecimal resolveTotalDsb(List<ShopOrderItem> items) {
        List<UUID> productIds = items.stream().map(ShopOrderItem::getProductId).distinct().toList();
        Map<UUID, CommerceProduct> commerceProducts = new HashMap<>();
        commerceProductRepository.findByProductIdIn(productIds)
                .forEach(product -> commerceProducts.put(product.getProductId(), product));

        BigDecimal total = ZERO;
        for (ShopOrderItem item : items) {
            CommerceProduct product = commerceProducts.get(item.getProductId());
            if (product == null || product.getDsbAmount() == null || product.getDsbAmount().signum() <= 0) {
                continue;
            }
            total = total.add(
                    product.getDsbAmount()
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
                            .setScale(4, RoundingMode.HALF_UP)
            );
        }
        return scale(total);
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    public record CommerceSettlementAdjustment(
            BigDecimal merchantNetAmount,
            BigDecimal totalDsbAmount,
            CommerceDistributionMode distributionMode
    ) {
    }
}
