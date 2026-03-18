package com.ccapp.admin.dto;

public record AdminCountryWorkspaceListItemResponse(
        String code,
        String name,
        AdminCountryWorkspaceStatus status,
        int readinessPercentage,
        String detailPath,
        AdminCountryWorkspaceActionResponse primaryAction
) {
}
