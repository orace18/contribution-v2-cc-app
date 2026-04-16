package com.ccapp.prayer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrayerBusinessDailyCatalogSeedRunner implements ApplicationRunner {

    private final PrayerBusinessDailyCatalogSeedService seedService;

    @Value("${prayer.business-daily-catalog.seed.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Prayer business daily catalog seed disabled");
            return;
        }

        PrayerBusinessDailyCatalogSeedService.SeedResult result = seedService.seedBusinessCatalog();
        log.info("Prayer business daily catalog seed completed processed={} inserted={} updated={}",
                result.processed(), result.inserted(), result.updated());
    }
}
