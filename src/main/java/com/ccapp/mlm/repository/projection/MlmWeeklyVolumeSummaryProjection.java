package com.ccapp.mlm.repository.projection;

import java.math.BigDecimal;

public interface MlmWeeklyVolumeSummaryProjection {
    Integer getWeekId();

    BigDecimal getTotalVolume();

    Long getEntriesCount();
}
