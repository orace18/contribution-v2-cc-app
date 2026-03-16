package com.ccapp.financing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OfferCountryConfigUpdateRequest(
        @NotNull @Valid List<OfferCountryConfigItemRequest> countries
) {
}
