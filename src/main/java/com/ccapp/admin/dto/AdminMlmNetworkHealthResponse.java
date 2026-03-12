package com.ccapp.admin.dto;

import java.time.OffsetDateTime;

public record AdminMlmNetworkHealthResponse(
        OffsetDateTime generatedAt,
        int currentWeekId,
        long membersWithMetrics,
        long weeklySnapshotsCount,
        boolean weeklyBonusExecutionHealthy,
        long deliveredOrdersWithoutVolumeLedger,
        boolean volumeLedgerUsageHealthy,
        long abnormalCommissionSpikes
) {
}
