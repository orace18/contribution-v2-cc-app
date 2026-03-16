package com.ccapp.mlm.scheduler;

import com.ccapp.mlm.service.MlmLedgerRebuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlmLedgerRebuildJob {

    private final MlmLedgerRebuildService mlmLedgerRebuildService;

    @Scheduled(cron = "${mlm.ledger-rebuild.cron:0 */2 * * * *}", zone = "${mlm.group-bonus.zone:Africa/Porto-Novo}")
    public void runPending() {
        int processed = mlmLedgerRebuildService.processPendingJobs();
        if (processed > 0) {
            log.info("mlm_ledger_rebuild_job_processed pendingJobs={}", processed);
        }
    }
}
