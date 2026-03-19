package com.ccapp.country.config.dto;

import java.util.List;

public record CountryConfigurationStatusResponse(
        List<CountryConfigurationStatusCountryResponse> notConfigured,
        List<CountryConfigurationStatusCountryResponse> draftOrPartial,
        List<CountryConfigurationStatusCountryResponse> configured
) {
}
