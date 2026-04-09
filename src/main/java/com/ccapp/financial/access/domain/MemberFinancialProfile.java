package com.ccapp.financial.access.domain;

import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MemberFinancialProfile(
        UUID accountHolderId,
        AccountStatus accountStatus,
        String accountRestrictionReason,
        MembershipStatus membershipStatus,
        OffsetDateTime membershipExpiresAt,
        boolean membershipActive,
        String walletStatus,
        boolean walletActive,
        boolean accountRestricted,
        FinancialAccessState financialReadiness,
        boolean imfChannelReady,
        String imfChannelStatus,
        List<FinancialAccessReasonCode> blockingReasons
) {
    public MemberFinancialProfile {
        blockingReasons = List.copyOf(blockingReasons == null ? List.of() : blockingReasons);
    }

    public boolean isFinanciallyAllowed() {
        return financialReadiness == FinancialAccessState.FINANCIAL_ALLOWED;
    }
}
