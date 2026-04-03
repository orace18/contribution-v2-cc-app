package com.ccapp.commerce.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommerceValidatePromoCodeResponse(
        boolean valid,
        String code,
        BigDecimal discountAmount,
        UUID ownerAccountHolderId,
        OffsetDateTime expiresAt
) {
}
