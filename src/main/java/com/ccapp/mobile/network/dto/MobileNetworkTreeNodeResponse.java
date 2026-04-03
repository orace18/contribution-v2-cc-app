package com.ccapp.mobile.network.dto;

import java.util.List;
import java.util.UUID;

public record MobileNetworkTreeNodeResponse(
        UUID accountHolderId,
        String name,
        String phone,
        String avatar,
        int generation,
        long downlineCount,
        MobileNetworkMembershipStatus membershipStatus,
        MobileNetworkNextAction nextAction,
        List<MobileNetworkTreeNodeResponse> children
) {
}
