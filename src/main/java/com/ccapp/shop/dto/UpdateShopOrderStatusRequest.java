package com.ccapp.shop.dto;

import com.ccapp.shop.domain.enums.ShopOrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateShopOrderStatusRequest(
        @NotNull(message = "status is required")
        ShopOrderStatus status
) {
}
