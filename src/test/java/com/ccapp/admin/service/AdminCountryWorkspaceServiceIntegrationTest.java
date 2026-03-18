package com.ccapp.admin.service;

import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AdminCountryWorkspaceService.class,
        CountrySetupBootstrapService.class,
        CountryConfigurationEngineService.class,
        CountryConfigReadinessService.class,
        CountryConfigResolver.class,
        ConfigAuditMetadataService.class,
        TestJacksonAndCachingConfig.class
})
class AdminCountryWorkspaceServiceIntegrationTest {

    @Autowired
    private AdminCountryWorkspaceService adminCountryWorkspaceService;
    @Autowired
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @Autowired
    private CountryRepository countryRepository;

    @Test
    void countriesOverview_shouldSeparateConfiguredAndUnconfiguredCountriesWithCountryBoundActions() {
        saveCountry("BJ", "Benin");
        saveCountry("SN", "Senegal");
        countryConfigurationEngineService.initializeCountryConfiguration("BJ", UUID.randomUUID());

        var response = adminCountryWorkspaceService.getCountriesOverview();

        assertEquals(1, response.configuredCountries().size());
        assertEquals("BJ", response.configuredCountries().get(0).code());
        assertEquals("Continue", response.configuredCountries().get(0).primaryAction().label());
        assertEquals("/countries/BJ/setup", response.configuredCountries().get(0).primaryAction().targetPath());
        assertTrue(response.configuredCountries().get(0).readinessPercentage() > 0);

        assertEquals(1, response.unconfiguredCountries().size());
        assertEquals("SN", response.unconfiguredCountries().get(0).code());
        assertEquals(0, response.unconfiguredCountries().get(0).readinessPercentage());
        assertEquals("Configure", response.unconfiguredCountries().get(0).primaryAction().label());
        assertEquals("/countries/SN/setup", response.unconfiguredCountries().get(0).primaryAction().targetPath());
    }

    @Test
    void countryWorkspace_shouldExposeReadOnlyTabsAndCountrySpecificRoutes() {
        saveCountry("GH", "Ghana");
        countryConfigurationEngineService.initializeCountryConfiguration("GH", UUID.randomUUID());

        var response = adminCountryWorkspaceService.getCountryWorkspace("GH");

        assertEquals("GH", response.code());
        assertEquals("Ghana", response.name());
        assertTrue(response.readOnly());
        assertEquals("/countries/GH", response.detailPath());
        assertEquals("/countries/GH/setup", response.setupPath());
        assertEquals(2, response.headerActions().size());
        assertEquals("Edit", response.headerActions().get(0).label());
        assertEquals("/countries/GH?mode=edit", response.headerActions().get(0).targetPath());
        assertEquals("Continue Setup", response.headerActions().get(1).label());
        assertEquals("/countries/GH/setup", response.headerActions().get(1).targetPath());
        assertEquals(7, response.tabs().size());
        assertEquals("general", response.tabs().get(0).key());
        assertEquals("accounting", response.tabs().get(1).key());
        assertEquals("membership", response.tabs().get(2).key());
        assertEquals("mlm", response.tabs().get(3).key());
        assertEquals("financing", response.tabs().get(4).key());
        assertEquals("payment", response.tabs().get(5).key());
        assertEquals("risk", response.tabs().get(6).key());
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
