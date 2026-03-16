package com.ccapp.circles.dto;

import java.util.UUID;

public record SendCircleInviteWhatsAppResponse(
        UUID inviteId,
        String recipientPhoneNumber,
        boolean delivered,
        String providerResponse
) {
}
