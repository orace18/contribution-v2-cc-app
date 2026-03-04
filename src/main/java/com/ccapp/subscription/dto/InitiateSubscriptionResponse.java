package com.ccapp.subscription.dto;

import java.util.UUID;

public record InitiateSubscriptionResponse(
        UUID operationId,
        String pspReference,
        String checkoutUrl,
        String status
) {
}
