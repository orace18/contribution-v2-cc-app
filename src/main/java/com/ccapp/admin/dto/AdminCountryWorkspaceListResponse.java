package com.ccapp.admin.dto;

import java.util.List;

public record AdminCountryWorkspaceListResponse(
        List<AdminCountryWorkspaceListItemResponse> configuredCountries,
        List<AdminCountryWorkspaceListItemResponse> unconfiguredCountries
) {
}
