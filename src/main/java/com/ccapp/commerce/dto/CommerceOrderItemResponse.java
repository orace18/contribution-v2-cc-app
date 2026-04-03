package com.ccapp.commerce.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CommerceOrderItemResponse(
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal commissionPercent,
        BigDecimal cvValue,
        BigDecimal dsbAmount
) {
}
