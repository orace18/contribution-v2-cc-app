package com.ccapp.credit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RecordLoanDisbursementRequest(
        @NotNull(message = "institutionId is required")
        UUID institutionId,

        @NotBlank(message = "referenceId is required")
        @Size(max = 128, message = "referenceId max size is 128")
        String referenceId
) {
}
