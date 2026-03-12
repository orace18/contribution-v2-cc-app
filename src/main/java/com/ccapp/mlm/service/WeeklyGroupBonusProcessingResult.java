package com.ccapp.mlm.service;

public record WeeklyGroupBonusProcessingResult(
        int weekId,
        int membersProcessed,
        int commissionsCreated,
        int carryoversStored
) {
}
