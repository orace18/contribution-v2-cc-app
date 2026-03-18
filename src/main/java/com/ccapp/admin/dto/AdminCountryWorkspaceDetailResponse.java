package com.ccapp.admin.dto;

import java.util.List;

public record AdminCountryWorkspaceDetailResponse(
        String code,
        String name,
        AdminCountryWorkspaceStatus status,
        int readinessPercentage,
        boolean readOnly,
        String detailPath,
        String setupPath,
        List<AdminCountryWorkspaceActionResponse> headerActions,
        List<AdminCountryWorkspaceTabResponse> tabs
) {
}
