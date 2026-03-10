package com.ccapp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OnboardingStep1Request(
        @NotBlank(message = "firstName is required")
        @Size(max = 120, message = "firstName max size is 120")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 120, message = "lastName max size is 120")
        String lastName,

        @NotBlank(message = "country is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "country must be ISO-2 uppercase")
        String country,

        @NotBlank(message = "phone is required")
        @Size(max = 32, message = "phone max size is 32")
        String phone
) {
}
