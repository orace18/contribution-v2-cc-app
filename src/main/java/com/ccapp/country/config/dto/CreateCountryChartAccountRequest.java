package com.ccapp.country.config.dto;

import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCountryChartAccountRequest(
        @NotNull CountryAccountGroup group,
        @NotBlank @Size(max = 180) String label
) {
}
