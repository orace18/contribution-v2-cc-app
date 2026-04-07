package com.ccapp.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminBackofficeOrderItemResponse(
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String productType
) {
}
