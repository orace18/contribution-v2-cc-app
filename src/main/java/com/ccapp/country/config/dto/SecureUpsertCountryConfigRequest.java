package com.ccapp.country.config.dto;

import com.ccapp.admin.dto.ConfigMutationApprovalRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record SecureUpsertCountryConfigRequest(
        @NotNull(message = "payload is required")
        JsonNode payload,
        OffsetDateTime effectiveDate,
        @Valid
        @NotNull(message = "approval is required")
        ConfigMutationApprovalRequest approval
) {
}
