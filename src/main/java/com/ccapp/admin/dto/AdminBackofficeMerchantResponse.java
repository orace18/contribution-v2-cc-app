package com.ccapp.admin.dto;

import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBackofficeMerchantResponse(
        UUID merchantId,
        String name,
        String contactName,
        String phone,
        String email,
        String countryCode,
        String walletAccountHolderId,
        String taxInformation,
        CommerceMerchantStatus status,
        int productsCount,
        int activeOrders,
        OffsetDateTime createdAt
) {
}
