package com.ccapp.commerce.dto;

import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.shop.domain.enums.ShopOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CommerceOrderResponse(
        UUID orderId,
        ShopOrderStatus status,
        UUID buyerAccountHolderId,
        UUID merchantId,
        String merchantName,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal netPaidAmount,
        BigDecimal totalDsbAmount,
        BigDecimal platformCommission,
        BigDecimal merchantNetAmount,
        BigDecimal volumeCv,
        String currency,
        String paymentMethod,
        UUID deliveryAddressId,
        boolean promoCodeApplied,
        String promoCode,
        CommerceDistributionMode distributionMode,
        CommerceSettlementStatus settlementStatus,
        OffsetDateTime createdAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime settledAt,
        List<CommerceOrderItemResponse> items,
        UUID paymentOperationId,
        boolean otpRequired,
        UUID otpChallengeId,
        OffsetDateTime otpExpiresAt
) {
}
