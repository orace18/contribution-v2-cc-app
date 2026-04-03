package com.ccapp.mobile.network.dto;

import java.util.UUID;

public record MobileNetworkMemberSummaryResponse(
        UUID accountHolderId,
        String firstName,
        String lastName,
        String avatar,
        int generation,
        long downlineCount,
        MobileNetworkMembershipStatus membershipStatus,
        MobileNetworkNextAction nextAction
) {
}
