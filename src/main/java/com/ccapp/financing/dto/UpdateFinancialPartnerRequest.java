package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.domain.enums.FinancialProductType;
import com.ccapp.financing.domain.enums.PartnerApiIntegrationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateFinancialPartnerRequest(
        @NotBlank String name,
        @NotNull FinancialPartnerType type,
        @NotBlank @Size(min = 2, max = 3) String countryCode,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Email @Size(max = 180) String contactEmail,
        @Size(max = 2000) String description,
        @NotNull FinancialPartnerStatus status,
        @NotEmpty List<FinancialProductType> supportedProducts,
        @NotNull PartnerApiIntegrationType apiIntegrationType,
        @NotBlank @Size(max = 64) String settlementAccount,
        @Size(max = 512) String apiEndpoint
) {
}
