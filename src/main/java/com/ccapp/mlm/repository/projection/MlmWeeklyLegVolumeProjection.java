package com.ccapp.mlm.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface MlmWeeklyLegVolumeProjection {
    UUID getAccountHolderId();

    UUID getPlacementChildId();

    BigDecimal getTotalVolumeCv();
}
