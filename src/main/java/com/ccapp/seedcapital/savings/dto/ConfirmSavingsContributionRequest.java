package com.ccapp.seedcapital.savings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConfirmSavingsContributionRequest(
        @NotNull @DecimalMin(value = "0.00") BigDecimal confirmedAmount,
        @Size(max = 128) String partnerReference,
        @Size(max = 128) String transactionReference,
        @Size(max = 500) String confirmationNote
) {
}
