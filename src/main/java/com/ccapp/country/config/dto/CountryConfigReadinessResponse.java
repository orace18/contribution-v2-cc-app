package com.ccapp.country.config.dto;

import java.util.List;

public record CountryConfigReadinessResponse(
        boolean configured,
        int score,
        CountryConfigReadinessStatus status,
        List<String> missing,
        List<CountryConfigReadinessSectionResponse> sections
) {
}
