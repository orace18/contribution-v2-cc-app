package com.ccapp.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminBackofficeOrderResponse(
        UUID id,
        UUID merchantId,
        String merchantName,
        UUID buyerAccountHolderId,
        String countryCode,
        String currency,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal netPaidAmount,
        BigDecimal totalDsbAmount,
        BigDecimal platformCommission,
        BigDecimal merchantNetAmount,
        BigDecimal volumeCv,
        boolean promoCodeApplied,
        String promoCode,
        String distributionMode,
        String settlementStatus,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime settledAt,
        String eventTitle,
        List<AdminBackofficeOrderItemResponse> items
) {
}
