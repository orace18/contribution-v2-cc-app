package com.ccapp.country.config.dto;

public record CountryConfigurationStatusCountryResponse(
        String code,
        String name,
        String status,
        int readinessPercentage
) {
}
