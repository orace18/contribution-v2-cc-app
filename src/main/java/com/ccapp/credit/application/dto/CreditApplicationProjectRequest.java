package com.ccapp.credit.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreditApplicationProjectRequest(
        @NotBlank(message = "project.name is required")
        @Size(max = 160, message = "project.name must be <= 160 characters")
        String name,

        @Size(max = 120, message = "project.sector must be <= 120 characters")
        String sector,

        @Size(max = 1000, message = "project.description must be <= 1000 characters")
        String description,

        @Size(max = 500, message = "project.useOfFunds must be <= 500 characters")
        String useOfFunds
) {
}
