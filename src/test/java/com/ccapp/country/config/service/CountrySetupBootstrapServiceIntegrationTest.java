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
        CountryChartOfAccountsService.class,
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
        saveCountry("ML", "Mali");
        saveCountry("NE", "Niger");

        List<CountrySetupCountryResponse> configured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> unconfigured = countrySetupBootstrapService.listUnconfiguredCountries();

        assertTrue(configured.isEmpty());
        assertTrue(unconfigured.size() >= 2);
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("ML")));
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("NE")));
        assertTrue(unconfigured.stream().allMatch(country -> !country.configured()));
    }

    @Test
    void shouldSplitConfiguredAndUnconfiguredCountriesWhenSomeAreInitialized() {
        saveCountry("ML", "Mali");
        saveCountry("NE", "Niger");
        countryConfigurationEngineService.initializeCountryConfiguration("ML", UUID.randomUUID());

        List<CountrySetupCountryResponse> configured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> unconfigured = countrySetupBootstrapService.listUnconfiguredCountries();

        assertEquals(1, configured.size());
        assertEquals("ML", configured.get(0).code());
        assertTrue(configured.get(0).configured());
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("NE")));
        assertFalse(unconfigured.stream().anyMatch(country -> country.code().equals("ML")));
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
    void shouldEnsureOperationalCountriesEvenWhenMasterTableIsNotEmpty() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("INT")
                .name("International")
                .currency("USD")
                .active(true)
                .configured(true)
                .fallback(true)
                .build());

        List<CountrySetupCountryResponse> unconfigured = countrySetupBootstrapService.listUnconfiguredCountries();

        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("BJ")));
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("CI")));
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("SN")));
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("GH")));
        assertTrue(unconfigured.stream().anyMatch(country -> country.code().equals("TG")));
    }

    @Test
    void initializeShouldMoveCountryFromUnconfiguredToConfigured() {
        saveCountry("CM", "Cameroon");

        List<CountrySetupCountryResponse> beforeConfigured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> beforeUnconfigured = countrySetupBootstrapService.listUnconfiguredCountries();
        assertTrue(beforeConfigured.isEmpty());
        assertTrue(beforeUnconfigured.stream().anyMatch(country -> country.code().equals("CM")));

        countryConfigurationEngineService.initializeCountryConfiguration("CM", UUID.randomUUID());

        List<CountrySetupCountryResponse> afterConfigured = countrySetupBootstrapService.listConfiguredCountries();
        List<CountrySetupCountryResponse> afterUnconfigured = countrySetupBootstrapService.listUnconfiguredCountries();
        assertTrue(afterConfigured.stream().anyMatch(country -> country.code().equals("CM")));
        assertFalse(afterUnconfigured.stream().anyMatch(country -> country.code().equals("CM")));
    }

    private void saveCountry(String code, String name) {
        Country country = countryRepository.findByCode(code).orElseGet(() -> Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .build());
        country.setName(name);
        country.setCurrency("XOF");
        country.setActive(true);
        country.setConfigured(false);
        country.setFallback(false);
        countryRepository.saveAndFlush(country);
    }
}
