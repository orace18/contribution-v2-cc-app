package com.ccapp.circlesocial.event;

import com.ccapp.common.events.DomainEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Deprecated(since = "2026-04", forRemoval = false)
public record CircleContributionEvent(
        UUID eventId,
        OffsetDateTime occurredAt,
        UUID circleId,
        UUID accountHolderId,
        UUID contributorUserId,
        BigDecimal amount,
        String currency
) implements DomainEvent {

    public static CircleContributionEvent create(UUID circleId,
                                                 UUID accountHolderId,
                                                 UUID contributorUserId,
                                                 BigDecimal amount,
                                                 String currency) {
        return new CircleContributionEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                circleId,
                accountHolderId,
                contributorUserId,
                amount,
                currency
        );
    }
}
