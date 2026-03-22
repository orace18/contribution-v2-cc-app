package com.ccapp.country.config.dto;

import java.util.List;

public record CountryConfigValidationResponse(
        boolean valid,
        List<CountryConfigValidationErrorResponse> errors
) {
}
