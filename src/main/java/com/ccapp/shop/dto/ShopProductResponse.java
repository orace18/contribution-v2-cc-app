package com.ccapp.shop.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopProductResponse(
        UUID id,
        UUID merchantId,
        String name,
        String description,
        BigDecimal price,
        String currency,
        BigDecimal commissionPercent,
        BigDecimal cvValue,
        boolean active
) {
}
