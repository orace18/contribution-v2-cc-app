package com.ccapp.financing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfferCountryConfigItemRequest(
        @NotBlank @Size(min = 2, max = 3) String countryCode,
        boolean enabled,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
