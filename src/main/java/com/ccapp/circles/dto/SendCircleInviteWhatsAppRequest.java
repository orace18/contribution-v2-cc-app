package com.ccapp.circles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendCircleInviteWhatsAppRequest(
        @NotBlank(message = "phoneNumber is required")
        @Size(max = 32, message = "phoneNumber max size is 32")
        String phoneNumber,

        @Size(max = 1000, message = "message max size is 1000")
        String message
) {
}
