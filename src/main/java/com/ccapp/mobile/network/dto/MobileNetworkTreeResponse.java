package com.ccapp.mobile.network.dto;

public record MobileNetworkTreeResponse(
        MobileNetworkTreeView view,
        long totalMembers,
        int maxDepth,
        MobileNetworkTreeNodeResponse root
) {
}
