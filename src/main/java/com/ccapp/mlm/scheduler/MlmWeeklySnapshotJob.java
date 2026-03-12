package com.ccapp.mlm.scheduler;

import com.ccapp.mlm.service.MlmAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlmWeeklySnapshotJob {

    private final MlmAnalyticsService mlmAnalyticsService;

    @Scheduled(cron = "${mlm.analytics.snapshot.cron:0 59 23 * * SUN}", zone = "${mlm.analytics.zone:Africa/Porto-Novo}")
    public void captureWeeklySnapshot() {
        int weekId = mlmAnalyticsService.currentWeekId();
        int membersSnapshotted = mlmAnalyticsService.captureWeekSnapshot(weekId);
        log.info("mlm_weekly_snapshot_captured weekId={} membersSnapshotted={}", weekId, membersSnapshotted);
    }
}
