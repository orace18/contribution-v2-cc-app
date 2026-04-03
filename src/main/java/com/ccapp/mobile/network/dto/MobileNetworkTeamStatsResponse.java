package com.ccapp.mobile.network.dto;

public record MobileNetworkTeamStatsResponse(
        long active,
        long inactive,
        long toFollowUp
) {
}
