package com.ccapp.prayer.dto;

public record PrayerStatsResponse(
        int amenCount,
        int supportCount,
        int commentCount
) {
}
