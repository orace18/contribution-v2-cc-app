package com.ccapp.prayer.dto;

import java.time.LocalDate;

public record PrayerRecentDayResponse(
        LocalDate date,
        boolean completed
) {
}
