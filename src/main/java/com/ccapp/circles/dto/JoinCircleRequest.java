package com.ccapp.circles.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record JoinCircleRequest(
        @Size(max = 255, message = "businessActivity max size is 255")
        String businessActivity,

        @Deprecated(since = "2026-04", forRemoval = false)
        @Size(max = 32, message = "loanStatus max size is 32")
        String loanStatus,

        @Deprecated(since = "2026-04", forRemoval = false)
        @DecimalMin(value = "0.00", message = "repaymentProgress must be >= 0")
        @DecimalMax(value = "100.00", message = "repaymentProgress must be <= 100")
        BigDecimal repaymentProgress
) {
}
