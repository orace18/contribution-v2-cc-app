package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.LoanApplicationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminFinancingApplicationListItemResponse(
        UUID id,
        UUID partnerId,
        String partnerName,
        UUID offerId,
        String offerName,
        UUID accountHolderId,
        String applicantName,
        String phoneNumber,
        String countryCode,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        String currency,
        LoanApplicationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
