package com.ccapp.country.dto;

public record CountryContext(
        String countryCode,
        String serviceCountryCode,
        String currency
) {
}
