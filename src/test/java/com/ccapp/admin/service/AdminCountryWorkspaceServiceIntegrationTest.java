package com.ccapp.admin.service;

import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AdminCountryWorkspaceService.class,
        CountrySetupBootstrapService.class,
        CountryChartOfAccountsService.class,
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
    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    void countriesOverview_shouldKeepInitializedButInvalidCountriesInUnconfiguredList() {
        saveCountry("BJ", "Benin");
        saveCountry("SN", "Senegal");
        countryConfigurationEngineService.initializeCountryConfiguration("BJ", UUID.randomUUID());

        var response = adminCountryWorkspaceService.getCountriesOverview();

        assertEquals(0, response.configuredCountries().size());

        assertTrue(response.unconfiguredCountries().size() >= 2);
        var benin = response.unconfiguredCountries().stream()
                .filter(item -> "BJ".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertEquals("Continue", benin.primaryAction().label());
        assertEquals("/countries/BJ/setup", benin.primaryAction().targetPath());
        assertTrue(benin.readinessPercentage() > 0);

        var senegal = response.unconfiguredCountries().stream()
                .filter(item -> "SN".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, senegal.readinessPercentage());
        assertEquals("Configure", senegal.primaryAction().label());
        assertEquals("/countries/SN/setup", senegal.primaryAction().targetPath());
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
        assertEquals(com.ccapp.admin.dto.AdminCountryWorkspaceStatus.NOT_CONFIGURED, response.status());
        assertEquals(2, response.headerActions().size());
        assertEquals("Edit", response.headerActions().get(0).label());
        assertEquals("/countries/GH?mode=edit", response.headerActions().get(0).targetPath());
        assertEquals("Continue Setup", response.headerActions().get(1).label());
        assertEquals("/countries/GH/setup", response.headerActions().get(1).targetPath());
        assertEquals(6, response.tabs().size());
        assertEquals("general", response.tabs().get(0).key());
        assertEquals("accounting", response.tabs().get(1).key());
        assertEquals("membership", response.tabs().get(2).key());
        assertEquals("mlm", response.tabs().get(3).key());
        assertEquals("financing", response.tabs().get(4).key());
        assertEquals("Financing not configured (optional)", response.tabs().get(4).missingItems().get(0));
        assertEquals("risk", response.tabs().get(5).key());
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
