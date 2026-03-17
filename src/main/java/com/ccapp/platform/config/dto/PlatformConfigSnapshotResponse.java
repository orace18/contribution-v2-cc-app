package com.ccapp.platform.config.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformConfigSnapshotResponse(
        OffsetDateTime asOf,
        List<PlatformConfigSnapshotItemResponse> configs
) {
}
