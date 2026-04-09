package com.ccapp.seedcapital.savings.dto;

import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleSavingsGateResponse(
        UUID circleId,
        String countryCode,
        ImfSavingsGateState state,
        int membersCount,
        int configuredChannelsCount,
        int readyChannelsCount,
        int qualifyingSavingsCyclesCount,
        int requiredSavingsCyclesCount,
        OffsetDateTime evaluatedAt
) {
}
