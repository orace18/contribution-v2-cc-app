package com.ccapp.mobile.network.dto;

import java.util.List;

public record MobileNetworkMembersResponse(
        List<MobileNetworkMemberSummaryResponse> members
) {
}
