package com.ccapp.admin.dto;

import com.ccapp.admin.domain.enums.AdminPolicyType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPolicyResponse(
        UUID id,
        AdminPolicyType policyType,
        String countryCode,
        String payload,
        OffsetDateTime createdAt
) {
}
