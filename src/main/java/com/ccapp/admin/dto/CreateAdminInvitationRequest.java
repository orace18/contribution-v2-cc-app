package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAdminInvitationRequest(
        @NotBlank @Email String email,
        @NotNull AdminRole role,
        UUID countryId,
        Long expiresInHours
) {
}
