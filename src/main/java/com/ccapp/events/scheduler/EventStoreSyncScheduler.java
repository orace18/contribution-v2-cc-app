package com.ccapp.events.scheduler;

import com.ccapp.events.service.EventStoreSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventStoreSyncScheduler {

    private final EventStoreSyncService eventStoreSyncService;

    @Scheduled(cron = "${events.store-sync.cron:0 */1 * * * *}", zone = "${mlm.group-bonus.zone:Africa/Porto-Novo}")
    public void runPendingJobs() {
        eventStoreSyncService.processPendingJobs();
    }
}
