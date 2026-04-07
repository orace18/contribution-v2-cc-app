package com.ccapp.admin.dto;

import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminBackofficeMerchantRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String contactName,
        @Size(max = 32) String phone,
        @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 2, max = 3) String country,
        @NotBlank String walletAccountHolderId,
        @Size(max = 255) String taxInformation,
        @NotNull CommerceMerchantStatus status
) {
}
