package com.ccapp.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record BlockAccountRequest(
        @NotBlank(message = "reason is required")
        String reason
) {
}
