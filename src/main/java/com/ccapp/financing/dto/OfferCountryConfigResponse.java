package com.ccapp.financing.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OfferCountryConfigResponse(
        UUID id,
        UUID offerId,
        String countryCode,
        boolean enabled,
        String currency,
        OffsetDateTime createdAt
) {
}
