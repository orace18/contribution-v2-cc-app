package com.ccapp.country.config.service;

import com.ccapp.country.config.dto.CountrySetupCountryResponse;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountrySetupBootstrapService {

    private static final List<SeedCountry> SAFE_DEFAULT_COUNTRIES = List.of(
            new SeedCountry("BJ", "Benin", "XOF", false),
            new SeedCountry("CI", "Cote d'Ivoire", "XOF", false),
            new SeedCountry("GH", "Ghana", "GHS", false),
            new SeedCountry("SN", "Senegal", "XOF", false),
            new SeedCountry("INT", "International", "USD", true)
    );

    private final CountryRepository countryRepository;
    private final CountryConfigVersionRepository countryConfigVersionRepository;

    @Transactional
    public void ensureMasterCountriesSeededIfEmpty() {
        long totalMasterCountries = countryRepository.count();
        if (totalMasterCountries > 0) {
            return;
        }

        List<Country> seedCountries = SAFE_DEFAULT_COUNTRIES.stream()
                .map(this::toCountryEntity)
                .toList();
        countryRepository.saveAll(seedCountries);
        log.warn(
                "Country master table was empty. Seeded {} safe countries for bootstrap.",
                seedCountries.size()
        );
    }

    @Transactional
    public List<CountrySetupCountryResponse> listConfiguredCountries() {
        CountrySetupLists lists = resolveSetupLists();
        return lists.configuredCountries();
    }

    @Transactional
    public List<CountrySetupCountryResponse> listUnconfiguredCountries() {
        CountrySetupLists lists = resolveSetupLists();
        return lists.unconfiguredCountries();
    }

    private CountrySetupLists resolveSetupLists() {
        ensureMasterCountriesSeededIfEmpty();

        long totalMasterCountries = countryRepository.count();
        List<Country> activeCountries = countryRepository.findByActiveTrueOrderByNameAsc();
        Set<String> initializedCountryCodes = countryConfigVersionRepository.findDistinctCountryCodes().stream()
                .map(code -> code == null ? null : code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());

        List<CountrySetupCountryResponse> configuredCountries = activeCountries.stream()
                .filter(country -> initializedCountryCodes.contains(country.getCode()))
                .map(country -> new CountrySetupCountryResponse(country.getCode(), country.getName(), true))
                .toList();

        List<CountrySetupCountryResponse> unconfiguredCountries = activeCountries.stream()
                .filter(country -> !initializedCountryCodes.contains(country.getCode()))
                .map(country -> new CountrySetupCountryResponse(country.getCode(), country.getName(), false))
                .toList();

        log.info(
                "Country setup bootstrap counts: totalMasterCountries={}, configuredCountriesCount={}, unconfiguredCountriesCount={}",
                totalMasterCountries,
                configuredCountries.size(),
                unconfiguredCountries.size()
        );

        return new CountrySetupLists(configuredCountries, unconfiguredCountries);
    }

    private Country toCountryEntity(SeedCountry seedCountry) {
        return Country.builder()
                .id(UUID.nameUUIDFromBytes(("country:" + seedCountry.code).getBytes(StandardCharsets.UTF_8)))
                .code(seedCountry.code)
                .name(seedCountry.name)
                .currency(seedCountry.currency)
                .active(true)
                .configured(false)
                .fallback(seedCountry.fallback)
                .build();
    }

    private record SeedCountry(String code, String name, String currency, boolean fallback) {
    }

    private record CountrySetupLists(
            List<CountrySetupCountryResponse> configuredCountries,
            List<CountrySetupCountryResponse> unconfiguredCountries
    ) {
    }
}
