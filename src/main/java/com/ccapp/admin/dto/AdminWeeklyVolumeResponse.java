package com.ccapp.admin.dto;

import java.math.BigDecimal;

public record AdminWeeklyVolumeResponse(
        int weekId,
        BigDecimal totalVolume,
        long entriesCount
) {
}
