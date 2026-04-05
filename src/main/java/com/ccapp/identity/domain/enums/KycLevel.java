package com.ccapp.identity.domain.enums;

public enum KycLevel {
    LEVEL_0_UNVERIFIED(0),
    LEVEL_1_BASIC(1),
    LEVEL_2_ENHANCED(2),
    LEVEL_3_FULL(3);

    private final int rank;

    KycLevel(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(KycLevel requiredLevel) {
        return rank >= requiredLevel.rank;
    }
}
