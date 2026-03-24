package com.ccapp.mlm.domain.enums;

import java.math.BigDecimal;

public enum MlmMemberGrade {
    CLASSIC(new BigDecimal("25000.0000")),
    GOLD(new BigDecimal("250000.0000")),
    DIAMOND(new BigDecimal("25000000.0000"));

    private final BigDecimal weeklyCap;

    MlmMemberGrade(BigDecimal weeklyCap) {
        this.weeklyCap = weeklyCap;
    }

    public BigDecimal weeklyCap() {
        return weeklyCap;
    }
}
