package com.ccapp.country.config.dto;

import com.ccapp.admin.dto.ConfigMutationApprovalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record SecureActivateCountrySetupRequest(
        @Valid
        @NotNull(message = "payloads is required")
        CountrySetupActivationPayloadsRequest payloads,
        OffsetDateTime effectiveDate,
        @Valid
        @NotNull(message = "approval is required")
        ConfigMutationApprovalRequest approval
) {
}
