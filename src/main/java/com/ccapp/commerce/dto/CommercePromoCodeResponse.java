package com.ccapp.commerce.dto;

import com.ccapp.commerce.domain.enums.CommercePromoCodeStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommercePromoCodeResponse(
        UUID id,
        String code,
        UUID ownerAccountHolderId,
        BigDecimal discountAmount,
        String country,
        CommercePromoCodeStatus status,
        OffsetDateTime expiryDate,
        Integer usageLimit,
        Integer usedCount
) {
}
