package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.dto.CountrySetupCountryResponse;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CountrySetupBootstrapService.class,
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class CountrySetupBootstrapServiceIntegrationTest {

    @Autowired
    private CountrySetupBootstrapService countrySetupBootstrapService;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryRepository countryRepository;

    @Test
    void shouldReturnAllActiveCountriesAsUnconfiguredWhenMasterExistsAndNoneInitialized() {
        saveCountry("BJ", "Benin");
        saveCountry("SN", "Senegal");

        List<CountrySetupCountryResponse> configured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> unconfigured = countrySetupBootstrapService.listUnconfiguredCountries();

        assertTrue(configured.isEmpty());
        assertEquals(2, unconfigured.size());
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("BJ")));
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("SN")));
        assertTrue(unconfigured.stream().allMatch(country -> !country.configured()));
    }

    @Test
    void shouldSplitConfiguredAndUnconfiguredCountriesWhenSomeAreInitialized() {
        saveCountry("BJ", "Benin");
        saveCountry("SN", "Senegal");
        countryConfigurationEngineService.initializeCountryConfiguration("BJ", UUID.randomUUID());

        List<CountrySetupCountryResponse> configured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> unconfigured = countrySetupBootstrapService.listUnconfiguredCountries();

        assertEquals(1, configured.size());
        assertEquals("BJ", configured.get(0).code());
        assertTrue(configured.get(0).configured());
        assertEquals(1, unconfigured.size());
        assertEquals("SN", unconfigured.get(0).code());
        assertFalse(unconfigured.get(0).configured());
    }

    @Test
    void shouldSeedSafeCountriesWhenMasterTableIsEmpty() {
        assertEquals(0, countryRepository.count());

        List<CountrySetupCountryResponse> unconfigured = countrySetupBootstrapService.listUnconfiguredCountries();
        List<CountrySetupCountryResponse> configured = countrySetupBootstrapService.listConfiguredCountries();

        assertTrue(countryRepository.count() > 0);
        assertTrue(unconfigured.size() > 0);
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("BJ")));
        assertTrue(configured.isEmpty());
    }

    @Test
    void initializeShouldMoveCountryFromUnconfiguredToConfigured() {
        saveCountry("CI", "Cote d'Ivoire");

        List<CountrySetupCountryResponse> beforeConfigured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> beforeUnconfigured = countrySetupBootstrapService.listUnconfiguredCountries();
        assertTrue(beforeConfigured.isEmpty());
        assertTrue(beforeUnconfigured.stream().anyMatch(country -> country.code().equals("CI")));

        countryConfigurationEngineService.initializeCountryConfiguration("CI", UUID.randomUUID());

        List<CountrySetupCountryResponse> afterConfigured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> afterUnconfigured = countrySetupBootstrapService.listUnconfiguredCountries();
        assertTrue(afterConfigured.stream().anyMatch(country -> country.code().equals("CI")));
        assertFalse(afterUnconfigured.stream().anyMatch(country -> country.code().equals("CI")));
    }

    private void saveCountry(String code, String name) {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .currency("XOF")
                .active(true)
                .configured(false)
                .fallback(false)
                .build());
    }
}
