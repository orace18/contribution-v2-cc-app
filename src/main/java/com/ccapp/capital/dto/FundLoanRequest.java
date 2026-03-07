package com.ccapp.capital.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FundLoanRequest(
        @NotNull(message = "capitalPoolId is required")
        UUID capitalPoolId
) {
}
