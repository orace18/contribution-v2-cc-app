package com.ccapp.country.config.service;

import com.ccapp.country.config.dto.CountrySetupCountryResponse;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.common.exception.BusinessException;
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

    private static final List<SeedCountry> CORE_OPERATIONAL_COUNTRIES = List.of(
            new SeedCountry("BJ", "Benin", "XOF", false),
            new SeedCountry("CI", "Cote d'Ivoire", "XOF", false),
            new SeedCountry("GH", "Ghana", "GHS", false),
            new SeedCountry("SN", "Senegal", "XOF", false),
            new SeedCountry("TG", "Togo", "XOF", false)
    );

    private static final List<SeedCountry> SAFE_DEFAULT_COUNTRIES = List.of(
            new SeedCountry("BJ", "Benin", "XOF", false),
            new SeedCountry("CI", "Cote d'Ivoire", "XOF", false),
            new SeedCountry("GH", "Ghana", "GHS", false),
            new SeedCountry("SN", "Senegal", "XOF", false),
            new SeedCountry("TG", "Togo", "XOF", false),
            new SeedCountry("INT", "International", "USD", true)
    );

    private final CountryRepository countryRepository;
    private final CountryConfigVersionRepository countryConfigVersionRepository;

    @Transactional
    public void ensureMasterCountriesSeededIfEmpty() {
        long totalMasterCountries = countryRepository.count();
        if (totalMasterCountries > 0) {
            ensureOperationalCoreCountriesAvailable();
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
        ensureOperationalCoreCountriesAvailable();
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

        if (activeCountries.isEmpty()) {
            log.error("No active countries available after fallback seed. totalMasterCountries={}", totalMasterCountries);
            throw new BusinessException("No active countries available");
        }

        List<CountrySetupCountryResponse> configuredCountries = activeCountries.stream()
                .filter(country -> initializedCountryCodes.contains(normalizeCountryCode(country.getCode())))
                .map(country -> new CountrySetupCountryResponse(normalizeCountryCode(country.getCode()), country.getName(), true))
                .toList();

        List<CountrySetupCountryResponse> unconfiguredCountries = activeCountries.stream()
                .filter(country -> !initializedCountryCodes.contains(normalizeCountryCode(country.getCode())))
                .map(country -> new CountrySetupCountryResponse(normalizeCountryCode(country.getCode()), country.getName(), false))
                .toList();

        log.info(
                "Country setup bootstrap counts: totalCountriesInDb={}, activeCountriesCount={}, configuredCountriesCount={}, unconfiguredCountriesCount={}",
                totalMasterCountries,
                activeCountries.size(),
                configuredCountries.size(),
                unconfiguredCountries.size()
        );

        return new CountrySetupLists(configuredCountries, unconfiguredCountries);
    }

    private void ensureOperationalCoreCountriesAvailable() {
        long updatedCount = CORE_OPERATIONAL_COUNTRIES.stream()
                .filter(this::upsertOperationalCountry)
                .count();
        if (updatedCount > 0) {
            log.warn("Country bootstrap fallback ensured operational countries: upsertedOrUpdated={}", updatedCount);
        }
    }

    private boolean upsertOperationalCountry(SeedCountry seedCountry) {
        Country existing = countryRepository.findByCode(seedCountry.code()).orElse(null);
        if (existing != null) {
            return patchOperationalCountry(existing, seedCountry);
        }
        countryRepository.save(toCountryEntity(seedCountry));
        return true;
    }

    private boolean patchOperationalCountry(Country existing, SeedCountry seedCountry) {
        boolean changed = false;
        if (!seedCountry.name().equals(existing.getName())) {
            existing.setName(seedCountry.name());
            changed = true;
        }
        if (!seedCountry.currency().equals(existing.getCurrency())) {
            existing.setCurrency(seedCountry.currency());
            changed = true;
        }
        if (!existing.isActive()) {
            existing.setActive(true);
            changed = true;
        }
        if (existing.isFallback() != seedCountry.fallback()) {
            existing.setFallback(seedCountry.fallback());
            changed = true;
        }
        if (changed) {
            countryRepository.save(existing);
        }
        return changed;
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "";
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
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
