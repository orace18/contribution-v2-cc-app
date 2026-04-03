package com.ccapp.events.dto;

import com.ccapp.events.domain.enums.EventProductSyncStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record EventTicketTypeResponse(
        UUID ticketTypeId,
        UUID eventId,
        String name,
        BigDecimal price,
        String currency,
        Integer stock,
        BigDecimal cv,
        BigDecimal platformCommissionRate,
        BigDecimal dsbAmount,
        UUID productId,
        EventProductSyncStatus productSyncStatus
) {
}
