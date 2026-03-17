package com.ccapp.country.config.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CountryConfigSnapshotResponse(
        String countryCode,
        OffsetDateTime asOf,
        List<CountryConfigSnapshotItemResponse> configs
) {
}
