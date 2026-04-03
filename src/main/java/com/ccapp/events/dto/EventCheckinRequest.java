package com.ccapp.events.dto;

import jakarta.validation.constraints.NotBlank;

public record EventCheckinRequest(
        @NotBlank(message = "qrCode is required")
        String qrCode
) {
}
