package com.ccapp.mobile.network.dto;

public record MobileNetworkEligibilityResponse(
        boolean hasSponsor,
        boolean phoneVerified,
        boolean hasPaidMembership,
        String membershipGrade,
        boolean hasNetworkPosition,
        boolean isEligible,
        String sponsorDecision,
        String placementStatus
) {
}
