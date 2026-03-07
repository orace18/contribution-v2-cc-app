package com.ccapp.country.dto;

import com.ccapp.country.domain.enums.CountryStatus;

public record CountryResponse(
        String code,
        String name,
        String currency,
        CountryStatus status,
        boolean internationalFallback
) {
}
