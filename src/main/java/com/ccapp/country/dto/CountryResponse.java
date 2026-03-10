package com.ccapp.country.dto;

public record CountryResponse(
        String code,
        String name,
        String currency,
        boolean active,
        boolean configured,
        boolean fallback
) {
}
