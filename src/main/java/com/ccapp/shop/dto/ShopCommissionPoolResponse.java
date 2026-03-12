package com.ccapp.shop.dto;

import java.math.BigDecimal;

public record ShopCommissionPoolResponse(
        BigDecimal merchantAmount,
        BigDecimal platformCommission,
        BigDecimal mlmVolume
) {
}
