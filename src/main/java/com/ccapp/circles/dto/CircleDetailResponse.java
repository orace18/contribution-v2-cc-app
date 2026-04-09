package com.ccapp.circles.dto;

import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleDetailResponse(
        UUID id,
        String name,
        CircleStatus status,
        UUID conversationId,
        int membersCount,
        OffsetDateTime nextMeetingAt,
        BigDecimal trustScore,
        ImfSavingsGateState savingsGateState,
        SeedCapitalCreditBridgeState creditCandidateState,
        CircleMeetingPolicyResponse meetingPolicy
) {
}
