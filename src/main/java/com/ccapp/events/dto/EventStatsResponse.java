package com.ccapp.events.dto;

import java.math.BigDecimal;

public record EventStatsResponse(
        long ticketsSold,
        BigDecimal revenue,
        long attendance,
        BigDecimal conversionRate
) {
}
