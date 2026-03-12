package com.ccapp.mlm.domain.enums;

import java.math.BigDecimal;

public enum MlmMemberGrade {
    CLASSIC(new BigDecimal("0.10"), new BigDecimal("25000.0000")),
    GOLD(new BigDecimal("0.12"), new BigDecimal("250000.0000")),
    DIAMOND(new BigDecimal("0.15"), new BigDecimal("25000000.0000"));

    private final BigDecimal bonusRate;
    private final BigDecimal weeklyCap;

    MlmMemberGrade(BigDecimal bonusRate, BigDecimal weeklyCap) {
        this.bonusRate = bonusRate;
        this.weeklyCap = weeklyCap;
    }

    public BigDecimal bonusRate() {
        return bonusRate;
    }

    public BigDecimal weeklyCap() {
        return weeklyCap;
    }
}
