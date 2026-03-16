package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.LoanApplicationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanApplicationResponse(
        UUID id,
        UUID offerId,
        UUID partnerId,
        UUID accountHolderId,
        UUID circleId,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        String currency,
        LoanApplicationStatus status,
        String partnerReference,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
