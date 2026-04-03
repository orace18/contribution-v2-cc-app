package com.ccapp.mobile.network.dto;

import java.util.UUID;

public record MobileNetworkFollowUpResponse(
        UUID memberId,
        String name,
        long inactiveDays,
        String phone
) {
}
