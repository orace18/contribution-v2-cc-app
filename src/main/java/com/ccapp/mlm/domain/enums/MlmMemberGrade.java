package com.ccapp.mlm.domain.enums;

import java.math.BigDecimal;

public enum MlmMemberGrade {
    // weeklyCap: max bonus per week; binaryRate: plan-based binary commission rate (%)
    CLASSIC(new BigDecimal("25000.0000"),    new BigDecimal("10")),
    GOLD(   new BigDecimal("250000.0000"),   new BigDecimal("12")),
    DIAMOND(new BigDecimal("25000000.0000"), new BigDecimal("15"));

    private final BigDecimal weeklyCap;
    private final BigDecimal binaryRate;

    MlmMemberGrade(BigDecimal weeklyCap, BigDecimal binaryRate) {
        this.weeklyCap = weeklyCap;
        this.binaryRate = binaryRate;
    }

    public BigDecimal weeklyCap() {
        return weeklyCap;
    }

    /** Plan-based binary commission rate as a percentage (e.g., 10 = 10%). */
    public BigDecimal binaryRate() {
        return binaryRate;
    }
}
