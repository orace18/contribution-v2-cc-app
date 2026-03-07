package com.ccapp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CountryConfigRequest(
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @NotBlank String name,
        boolean active
) {
}
