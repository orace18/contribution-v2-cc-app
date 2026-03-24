package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFinancialPartnerStatusRequest(
        @NotNull FinancialPartnerStatus status
) {
}
