package com.ccapp.payments.subscription.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InitiateSubscriptionResponse(
        UUID operationId,
        String pspReference,
        String checkoutUrl,
        String status,
        boolean otpRequired,
        UUID otpChallengeId,
        OffsetDateTime otpExpiresAt
) {
}
