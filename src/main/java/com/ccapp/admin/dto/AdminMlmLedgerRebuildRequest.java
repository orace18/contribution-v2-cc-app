package com.ccapp.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminMlmLedgerRebuildRequest(
        @Min(value = 10, message = "batchSize must be >= 10")
        @Max(value = 5000, message = "batchSize must be <= 5000")
        Integer batchSize
) {
}
