package com.ccapp.mobile.home.dto;

import java.util.List;

public record MobileHomeNotificationSummaryResponse(
        long total,
        List<MobileHomeNotificationItemResponse> latest
) {
}
