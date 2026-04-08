package com.ccapp.prayer.dto;

public record PrayerViewerStateResponse(
        boolean hasAmened,
        boolean hasSupported,
        boolean canAmen,
        boolean canSupport,
        boolean canComment
) {
}
