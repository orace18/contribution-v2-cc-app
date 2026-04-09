package com.ccapp.seedcapital.savings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateSavingsCycleRequest(
        @NotNull OffsetDateTime windowStart,
        @NotNull OffsetDateTime windowEnd,
        @NotNull @DecimalMin(value = "0.01") BigDecimal expectedAmountPerMember,
        @Size(max = 500) String note
) {
}
