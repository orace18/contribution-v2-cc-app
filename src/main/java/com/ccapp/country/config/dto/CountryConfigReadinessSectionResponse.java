package com.ccapp.country.config.dto;

import java.util.List;

public record CountryConfigReadinessSectionResponse(
        String section,
        int passedChecks,
        int totalChecks,
        List<String> missing
) {
}
