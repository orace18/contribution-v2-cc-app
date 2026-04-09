package com.ccapp.seedcapital.savings.dto;

import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsChannelStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImfSavingsChannelResponse(
        UUID id,
        UUID accountHolderId,
        UUID partnerId,
        FinancialPartnerType partnerType,
        String countryCode,
        ImfSavingsChannelStatus status,
        boolean ready,
        String partnerMemberReference,
        String externalChannelReference,
        String readinessNote,
        OffsetDateTime configuredAt,
        OffsetDateTime activatedAt,
        OffsetDateTime updatedAt
) {
}
