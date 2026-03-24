package com.ccapp.shop.dto;

import com.ccapp.shop.domain.enums.ShopOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ShopOrderResponse(
        UUID id,
        UUID buyerId,
        ShopOrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String paymentMethod,
        UUID deliveryAddressId,
        OffsetDateTime createdAt,
        List<ShopOrderItemResponse> items,
        ShopCommissionPoolResponse commissionPool,
        UUID paymentOperationId,
        boolean otpRequired,
        UUID otpChallengeId,
        OffsetDateTime otpExpiresAt
) {
}
