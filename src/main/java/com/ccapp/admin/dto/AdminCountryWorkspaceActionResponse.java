package com.ccapp.admin.dto;

public record AdminCountryWorkspaceActionResponse(
        AdminCountryWorkspaceAction action,
        String label,
        String targetPath
) {
}
