package com.ccapp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPolicyRequest(
        @Size(min = 2, max = 3) String countryCode,
        @NotBlank String payload
) {
}
