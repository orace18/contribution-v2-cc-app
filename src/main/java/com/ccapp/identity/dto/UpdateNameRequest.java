package com.ccapp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNameRequest(
        @NotBlank(message = "firstName is required")
        @Size(max = 120, message = "firstName max size is 120")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 120, message = "lastName max size is 120")
        String lastName
) {
}
