package com.ccapp.country.config.service;

import com.ccapp.admin.service.ConfigAuditMetadataService;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.payments.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

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
        CountryConfigurationStatusService.class,
        CountrySetupBootstrapService.class,
        CountryChartOfAccountsService.class,
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class CountryConfigurationStatusServiceIntegrationTest {

    @Autowired
    private CountryConfigurationStatusService countryConfigurationStatusService;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryRepository countryRepository;
    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    void shouldIncludeActiveWorldCountriesInNotConfiguredWhenNoConfigAttemptExists() {
        saveCountry("ML", "Mali");

        var response = countryConfigurationStatusService.getConfigurationStatus();

        assertTrue(response.notConfigured().stream().anyMatch(country -> country.code().equals("ML")));
        assertFalse(response.draftOrPartial().stream().anyMatch(country -> country.code().equals("ML")));
        assertFalse(response.configured().stream().anyMatch(country -> country.code().equals("ML")));
    }

    @Test
    void initializeShouldMoveCountryFromNotConfiguredToDraftOrPartialWithBinaryStatus() {
        saveCountry("CM", "Cameroon");

        var before = countryConfigurationStatusService.getConfigurationStatus();
        assertTrue(before.notConfigured().stream().anyMatch(country -> country.code().equals("CM")));

        countryConfigurationEngineService.initializeCountryConfiguration("CM", UUID.randomUUID());

        var after = countryConfigurationStatusService.getConfigurationStatus();
        assertFalse(after.notConfigured().stream().anyMatch(country -> country.code().equals("CM")));
        assertTrue(after.draftOrPartial().stream().anyMatch(country -> country.code().equals("CM")));
        assertEquals(
                "NOT_CONFIGURED",
                after.draftOrPartial().stream()
                        .filter(country -> country.code().equals("CM"))
                        .findFirst()
                        .orElseThrow()
                        .status()
        );
    }

    @Test
    void shouldListCountryAsConfiguredWhenInitializedAndMarkedConfigured() {
        Country country = saveCountry("GH", "Ghana");
        countryConfigurationEngineService.initializeCountryConfiguration("GH", UUID.randomUUID());
        country.setConfigured(true);
        countryRepository.saveAndFlush(country);

        var response = countryConfigurationStatusService.getConfigurationStatus();

        assertFalse(response.notConfigured().stream().anyMatch(item -> item.code().equals("GH")));
        assertFalse(response.draftOrPartial().stream().anyMatch(item -> item.code().equals("GH")));
        assertTrue(response.configured().stream().anyMatch(item -> item.code().equals("GH")));
        assertEquals(
                "CONFIGURED",
                response.configured().stream()
                        .filter(item -> item.code().equals("GH"))
                        .findFirst()
                        .orElseThrow()
                        .status()
        );
    }

    private Country saveCountry(String code, String name) {
        Country country = countryRepository.findByCode(code).orElseGet(() -> Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .build());
        country.setName(name);
        country.setCurrency("XOF");
        country.setActive(true);
        country.setConfigured(false);
        country.setFallback(false);
        return countryRepository.saveAndFlush(country);
    }
}
