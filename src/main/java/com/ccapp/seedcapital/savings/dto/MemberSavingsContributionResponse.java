package com.ccapp.seedcapital.savings.dto;

import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionConfirmationSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberSavingsContributionResponse(
        UUID id,
        UUID cycleId,
        UUID accountHolderId,
        BigDecimal expectedAmount,
        BigDecimal confirmedAmount,
        MemberSavingsContributionStatus status,
        OffsetDateTime confirmedAt,
        String partnerReference,
        String transactionReference,
        SavingsContributionConfirmationSource confirmationSource,
        String confirmationNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
