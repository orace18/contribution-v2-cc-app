package com.ccapp.admin.dto;

import com.ccapp.mlm.domain.enums.MlmLedgerRebuildJobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminMlmLedgerRebuildResponse(
        UUID jobId,
        MlmLedgerRebuildJobStatus status,
        int batchSize,
        OffsetDateTime requestedAt
) {
}
