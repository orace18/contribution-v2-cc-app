package com.ccapp.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBackofficeProductResponse(
        UUID id,
        UUID merchantId,
        String merchantName,
        String countryCode,
        String name,
        String description,
        BigDecimal price,
        String currency,
        BigDecimal cvValue,
        BigDecimal commissionPercent,
        BigDecimal dsbAmount,
        String deliveryType,
        String originType,
        String status,
        String eventTitle,
        String ticketTypeName,
        String imageUrl,
        OffsetDateTime createdAt
) {
}
