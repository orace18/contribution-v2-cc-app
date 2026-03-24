package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewLoanApplicationRequest(
        @NotNull LoanApplicationStatus status,
        @DecimalMin("0.0001") BigDecimal approvedAmount,
        @Size(max = 128) String partnerReference,
        @Size(max = 1000) String reviewComment
) {
}
