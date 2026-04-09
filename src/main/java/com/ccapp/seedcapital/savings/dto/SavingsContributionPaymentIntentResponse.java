package com.ccapp.seedcapital.savings.dto;

import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionPaymentProvider;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SavingsContributionPaymentIntentResponse(
        UUID intentId,
        SavingsContributionPaymentProvider provider,
        String status,
        UUID operationId,
        UUID otpChallengeId,
        OffsetDateTime otpExpiresAt
) {
}
