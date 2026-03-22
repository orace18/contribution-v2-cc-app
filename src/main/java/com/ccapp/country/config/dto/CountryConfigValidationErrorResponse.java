package com.ccapp.country.config.dto;

public record CountryConfigValidationErrorResponse(
        String field,
        String message
) {
}
