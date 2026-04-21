package com.ccapp.commerce.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CommerceProductResponse(
        UUID productId,
        UUID merchantId,
        String merchantName,
        String name,
        String description,
        BigDecimal price,
        String currency,
        BigDecimal cvValue,
        BigDecimal commissionPercent,
        BigDecimal dsbAmount,
        boolean active,
        String imageUrl
) {
}
