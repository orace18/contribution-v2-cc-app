package com.ccapp.financial.access.dto;

import com.ccapp.financial.access.domain.FinancialAccessReasonCode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MemberFinancialProfileResponse(
        UUID accountHolderId,
        String membershipStatus,
        OffsetDateTime membershipExpiresAt,
        boolean walletActive,
        boolean walletPinConfigured,
        String financialAccessState,
        List<FinancialAccessReasonCode> financialAccessReasonCodes,
        String imfChannelStatus,
        boolean imfChannelReady
) {
}
