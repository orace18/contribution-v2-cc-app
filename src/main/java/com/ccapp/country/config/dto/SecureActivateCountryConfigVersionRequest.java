package com.ccapp.country.config.dto;

import com.ccapp.admin.dto.ConfigMutationApprovalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record SecureActivateCountryConfigVersionRequest(
        @NotNull(message = "version is required")
        Integer version,
        OffsetDateTime effectiveDate,
        @Valid
        @NotNull(message = "approval is required")
        ConfigMutationApprovalRequest approval
) {
}
