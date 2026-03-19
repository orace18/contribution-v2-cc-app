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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        countrySetupBootstrapService.ensureMasterCountriesSeededIfEmpty();
        int configuredCount = countrySetupBootstrapService.listConfiguredCountries().size();
        int unconfiguredCount = countrySetupBootstrapService.listUnconfiguredCountries().size();
        log.info(
                "Country master startup check completed: configuredCountriesCount={}, unconfiguredCountriesCount={}",
                configuredCount,
                unconfiguredCount
        );
    }
}
