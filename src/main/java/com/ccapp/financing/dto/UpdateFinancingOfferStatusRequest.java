package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFinancingOfferStatusRequest(
        @NotNull FinancingOfferStatus status
) {
}
