package com.ccapp.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConfigAuditMetadataResponse(
        Integer version,
        String status,
        OffsetDateTime effectiveDate,
        OffsetDateTime createdAt,
        UUID createdBy,
        OffsetDateTime lastModifiedAt,
        UUID lastModifiedBy,
        UUID approvedBy,
        String reasonForChange
) {
}
