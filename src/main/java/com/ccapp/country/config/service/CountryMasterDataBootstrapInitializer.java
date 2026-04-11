package com.ccapp.country.config.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CountryMasterDataBootstrapInitializer implements ApplicationRunner {

    private final CountrySetupBootstrapService countrySetupBootstrapService;
    private final CountryConfigurationEngineService countryConfigurationEngineService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        countrySetupBootstrapService.ensureMasterCountriesSeededIfEmpty();
        try {
            var bjRepair = countryConfigurationEngineService.repairActiveSubscriptionPostingSafety("BJ", null);
            log.info("Country runtime safety repair completed: countryCode={}, patched={}, defect={}, activeGeneralVersionBefore={}, activeGeneralVersionAfter={}",
                    bjRepair.countryCode(),
                    bjRepair.patched(),
                    bjRepair.defect(),
                    bjRepair.activeGeneralVersionBefore(),
                    bjRepair.activeGeneralVersionAfter());
        } catch (Exception ex) {
            log.error("Country runtime safety repair failed for BJ: {}", ex.getMessage(), ex);
        }
        int configuredCount = countrySetupBootstrapService.listConfiguredCountries().size();
        int unconfiguredCount = countrySetupBootstrapService.listUnconfiguredCountries().size();
        log.info(
                "Country master startup check completed: configuredCountriesCount={}, unconfiguredCountriesCount={}",
                configuredCount,
                unconfiguredCount
        );
    }
}
