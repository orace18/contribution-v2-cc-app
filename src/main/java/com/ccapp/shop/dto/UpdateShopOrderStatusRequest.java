package com.ccapp.shop.dto;

import com.ccapp.shop.domain.enums.ShopOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record UpdateShopOrderStatusRequest(
        @NotNull(message = "status is required")
        ShopOrderStatus status,

        @Pattern(regexp = "\\d{4,8}", message = "PIN must be 4 to 8 digits")
        String pin,

        UUID otpChallengeId,

        @Pattern(regexp = "\\d{4,8}", message = "OTP code must be 4 to 8 digits")
        String otpCode
) {
}
