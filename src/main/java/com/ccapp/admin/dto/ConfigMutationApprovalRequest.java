package com.ccapp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConfigMutationApprovalRequest(
        @NotBlank(message = "password is required")
        String password,
        @NotBlank(message = "twoFactorCode is required")
        String twoFactorCode,
        @NotBlank(message = "reasonForChange is required")
        @Size(max = 500, message = "reasonForChange must be <= 500 characters")
        String reasonForChange,
        UUID approvedByAdminUserId
) {
}
