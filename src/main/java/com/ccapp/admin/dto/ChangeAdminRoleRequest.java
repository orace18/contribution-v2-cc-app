package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeAdminRoleRequest(
        @NotNull AdminRole role,
        UUID countryId
) {
}
