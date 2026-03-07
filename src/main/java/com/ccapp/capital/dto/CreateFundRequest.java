package com.ccapp.capital.dto;

import com.ccapp.capital.domain.enums.FundProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateFundRequest(
        @NotBlank(message = "name is required")
        @Size(max = 128, message = "name max size is 128")
        String name,

        @NotNull(message = "provider is required")
        FundProviderType provider,

        @NotNull(message = "totalCapital is required")
        @DecimalMin(value = "0.01", message = "totalCapital must be > 0")
        BigDecimal totalCapital,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be ISO-3")
        String currency
) {
}
