package com.ccapp.shop.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopOrderItemResponse(
        UUID id,
        UUID productId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal commissionPercent,
        BigDecimal cvValue
) {
}
