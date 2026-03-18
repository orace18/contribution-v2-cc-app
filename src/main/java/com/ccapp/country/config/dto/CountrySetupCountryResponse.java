package com.ccapp.country.config.dto;

public record CountrySetupCountryResponse(
        String code,
        String name,
        boolean configured
) {
}
