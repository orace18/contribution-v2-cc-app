package com.ccapp.country.config.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.dto.CountryConfigReadinessResponse;
import com.ccapp.country.config.dto.CountryConfigReadinessStatus;
import com.ccapp.country.config.dto.CountryConfigurationStatusCountryResponse;
import com.ccapp.country.config.dto.CountryConfigurationStatusResponse;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryConfigurationStatusService {

    private final CountryRepository countryRepository;
    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryConfigReadinessService countryConfigReadinessService;
    private final CountrySetupBootstrapService countrySetupBootstrapService;

    @Transactional
    public CountryConfigurationStatusResponse getConfigurationStatus() {
        countrySetupBootstrapService.ensureMasterCountriesSeededIfEmpty();

        List<Country> activeWorldCountries = countryRepository.findByActiveTrueOrderByNameAsc();
        if (activeWorldCountries.isEmpty()) {
            throw new BusinessException("No active countries available");
        }

        Set<String> initializedCountryCodes = countryConfigVersionRepository.findDistinctCountryCodes()
                .stream()
                .map(this::normalizeCountryCode)
                .filter(code -> !code.isBlank())
                .collect(Collectors.toSet());

        List<CountryConfigurationStatusCountryResponse> notConfigured = new ArrayList<>();
        List<CountryConfigurationStatusCountryResponse> draftOrPartial = new ArrayList<>();
        List<CountryConfigurationStatusCountryResponse> configured = new ArrayList<>();

        long configAttemptsCount = 0;
        for (Country country : activeWorldCountries) {
            String countryCode = normalizeCountryCode(country.getCode());
            if (!initializedCountryCodes.contains(countryCode)) {
                notConfigured.add(new CountryConfigurationStatusCountryResponse(
                        countryCode,
                        country.getName(),
                        "NOT_CONFIGURED",
                        0
                ));
                continue;
            }

            configAttemptsCount++;
            CountryConfigReadinessResponse readiness = countryConfigReadinessService.evaluateReadiness(countryCode);
            boolean isConfigured = readiness.configured() || country.isConfigured()
                    || readiness.status() == CountryConfigReadinessStatus.CONFIGURED
                    || readiness.status() == CountryConfigReadinessStatus.READY;
            if (isConfigured) {
                configured.add(new CountryConfigurationStatusCountryResponse(
                        countryCode,
                        country.getName(),
                        readiness.status().name(),
                        readiness.score()
                ));
            } else {
                draftOrPartial.add(new CountryConfigurationStatusCountryResponse(
                        countryCode,
                        country.getName(),
                        readiness.status().name(),
                        readiness.score()
                ));
            }
        }

        log.info(
                "Country configuration status counts: totalWorldCountriesLoaded={}, configAttemptedCountriesCount={}, configuredCountriesCount={}, countriesAvailableForNewSetupCount={}, draftOrPartialCount={}",
                activeWorldCountries.size(),
                configAttemptsCount,
                configured.size(),
                notConfigured.size(),
                draftOrPartial.size()
        );

        return new CountryConfigurationStatusResponse(notConfigured, draftOrPartial, configured);
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "";
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
