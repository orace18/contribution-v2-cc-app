package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.FinancialProductType;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminFinancingApplicationDetailsResponse(
        UUID id,
        UUID partnerId,
        String partnerName,
        UUID offerId,
        String offerName,
        FinancialProductType offerType,
        String offerCountryCode,
        AdminFinancingApplicationApplicantResponse applicant,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        String currency,
        LoanApplicationStatus status,
        String partnerReference,
        String decisionNote,
        String configSnapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        AdminFinancingApplicationScoringResponse scoring,
        List<AdminFinancingApplicationDocumentResponse> documents
) {
}
