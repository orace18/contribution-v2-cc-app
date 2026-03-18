package com.ccapp.country.config.dto;

import java.util.List;

public record CountryChartAccountHistoryResponse(
        CountryChartAccountDetailsResponse account,
        List<CountryChartAccountMovementResponse> movements
) {
}
