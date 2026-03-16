package com.ccapp.shop.dto;

import jakarta.validation.constraints.Size;

public record RefundShopOrderRequest(
        @Size(max = 255, message = "reason max size is 255")
        String reason
) {
}
