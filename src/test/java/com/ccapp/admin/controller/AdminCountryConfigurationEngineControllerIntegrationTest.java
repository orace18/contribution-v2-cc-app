package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.dto.AdminCountryWorkspaceAction;
import com.ccapp.admin.dto.AdminCountryWorkspaceActionResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceDetailResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceListItemResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceListResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceStatus;
import com.ccapp.admin.dto.AdminCountryWorkspaceTabResponse;
import com.ccapp.admin.repository.AdminAuditLogRepository;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.admin.service.AdminConfigMutationSecurityService;
import com.ccapp.admin.service.AdminCountryWorkspaceService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.config.service.CountryConfigurationStatusService;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.dto.CountryChartAccountDetailsResponse;
import com.ccapp.country.config.dto.CountryChartAccountSummaryResponse;
import com.ccapp.country.config.dto.CreateCountryChartAccountRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AdminCountryConfigurationEngineController.class,
        AdminAuditService.class,
        TestJacksonAndCachingConfig.class
})
class AdminCountryConfigurationEngineControllerIntegrationTest {

    @Autowired
    private AdminCountryConfigurationEngineController controller;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @MockBean
    private CountryConfigurationEngineService countryConfigurationEngineService;
    @MockBean
    private CountryConfigReadinessService countryConfigReadinessService;
    @MockBean
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @MockBean
    private CountrySetupBootstrapService countrySetupBootstrapService;
    @MockBean
    private CountryConfigurationStatusService countryConfigurationStatusService;
    @MockBean
    private AdminCountryWorkspaceService adminCountryWorkspaceService;
    @MockBean
    private AdminConfigMutationSecurityService adminConfigMutationSecurityService;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        AdminPrincipal principal = AdminPrincipal.builder()
                .adminUserId(adminId)
                .email("ops-admin@ccapp.io")
                .role(AdminRole.ROLE_SUPER_ADMIN)
                .countryId(null)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(AdminRole.ROLE_SUPER_ADMIN.name()))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCountriesWorkspace_shouldReturnCountryFirstOverviewAndWriteAuditLog() {
        AdminCountryWorkspaceListResponse workspace = new AdminCountryWorkspaceListResponse(
                List.of(new AdminCountryWorkspaceListItemResponse(
                        "BJ",
                        "Benin",
                        AdminCountryWorkspaceStatus.DRAFT,
                        18,
                        "/countries/BJ",
                        new AdminCountryWorkspaceActionResponse(
                                AdminCountryWorkspaceAction.CONTINUE,
                                "Continue",
                                "/countries/BJ/setup"
                        )
                )),
                List.of(new AdminCountryWorkspaceListItemResponse(
                        "SN",
                        "Senegal",
                        AdminCountryWorkspaceStatus.NOT_CONFIGURED,
                        0,
                        "/countries/SN",
                        new AdminCountryWorkspaceActionResponse(
                                AdminCountryWorkspaceAction.CONFIGURE,
                                "Configure",
                                "/countries/SN/setup"
                        )
                ))
        );
        when(adminCountryWorkspaceService.getCountriesOverview()).thenReturn(workspace);

        ResponseEntity<CommonResponse<AdminCountryWorkspaceListResponse>> response = controller.getCountriesWorkspace();

        assertEquals(1, response.getBody().data().configuredCountries().size());
        assertEquals(1, response.getBody().data().unconfiguredCountries().size());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "COUNTRY_CONFIG_WORKSPACE",
                null,
                "LIST_COUNTRY_WORKSPACE_COUNTRIES"
        ));
    }

    @Test
    void getCountryWorkspace_shouldReturnCountryDetailAndWriteAuditLog() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode generalPayload = objectMapper.createObjectNode();
        generalPayload.put("currency", "XOF");

        AdminCountryWorkspaceDetailResponse workspace = new AdminCountryWorkspaceDetailResponse(
                "BJ",
                "Benin",
                AdminCountryWorkspaceStatus.DRAFT,
                18,
                true,
                "/countries/BJ",
                "/countries/BJ/setup",
                List.of(
                        new AdminCountryWorkspaceActionResponse(AdminCountryWorkspaceAction.EDIT, "Edit", "/countries/BJ?mode=edit"),
                        new AdminCountryWorkspaceActionResponse(AdminCountryWorkspaceAction.CONTINUE_SETUP, "Continue Setup", "/countries/BJ/setup")
                ),
                List.of(
                        new AdminCountryWorkspaceTabResponse("general", "General", AdminCountryWorkspaceStatus.DRAFT, 50, List.of("general.currency"), 1, null, generalPayload)
                )
        );
        when(adminCountryWorkspaceService.getCountryWorkspace("BJ")).thenReturn(workspace);

        ResponseEntity<CommonResponse<AdminCountryWorkspaceDetailResponse>> response = controller.getCountryWorkspace("BJ");

        assertEquals("BJ", response.getBody().data().code());
        assertEquals("/countries/BJ/setup", response.getBody().data().setupPath());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "COUNTRY_CONFIG_WORKSPACE",
                "BJ",
                "GET_COUNTRY_WORKSPACE"
        ));
    }

    @Test
    void getConfigurationStatus_shouldReturnGroupedCountriesAndWriteAuditLog() {
        var status = new com.ccapp.country.config.dto.CountryConfigurationStatusResponse(
                List.of(new com.ccapp.country.config.dto.CountryConfigurationStatusCountryResponse("NE", "Niger", "NOT_CONFIGURED", 0)),
                List.of(new com.ccapp.country.config.dto.CountryConfigurationStatusCountryResponse("BJ", "Benin", "DRAFT", 18)),
                List.of(new com.ccapp.country.config.dto.CountryConfigurationStatusCountryResponse("GH", "Ghana", "READY", 100))
        );
        when(countryConfigurationStatusService.getConfigurationStatus()).thenReturn(status);

        ResponseEntity<CommonResponse<com.ccapp.country.config.dto.CountryConfigurationStatusResponse>> response =
                controller.getConfigurationStatus();

        assertEquals(1, response.getBody().data().notConfigured().size());
        assertEquals(1, response.getBody().data().draftOrPartial().size());
        assertEquals(1, response.getBody().data().configured().size());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "COUNTRY_CONFIG",
                null,
                "GET_COUNTRY_CONFIGURATION_STATUS"
        ));
    }

    @Test
    void createCountryAccount_shouldReturnCreatedAccountAndWriteAuditLog() {
        UUID accountId = UUID.randomUUID();
        CountryChartAccountDetailsResponse account = new CountryChartAccountDetailsResponse(
                accountId,
                "BJ",
                "220000001",
                "220000001",
                "Platform Revenue",
                "Platform Revenue",
                CountryAccountGroup.PLATFORM_REVENUE,
                CountryChartAccountType.REVENUE,
                "XOF",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CountryChartAccountStatus.ACTIVE,
                null,
                null,
                null
        );
        when(countryChartOfAccountsService.createCountryAccount("BJ",
                new CreateCountryChartAccountRequest(CountryAccountGroup.PLATFORM_REVENUE, "Platform Revenue")))
                .thenReturn(account);

        ResponseEntity<CommonResponse<CountryChartAccountDetailsResponse>> response = controller.createCountryAccount(
                "BJ",
                new CreateCountryChartAccountRequest(CountryAccountGroup.PLATFORM_REVENUE, "Platform Revenue")
        );

        assertNotNull(response.getBody());
        assertEquals(accountId, response.getBody().data().id());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "COUNTRY_ACCOUNT",
                accountId.toString(),
                "CREATE_COUNTRY_CHART_ACCOUNT"
        ));
    }

    @Test
    void autoGenerateCountryAccounts_shouldReturnAccountsAndWriteAuditLog() {
        List<CountryChartAccountSummaryResponse> accounts = List.of(
                new CountryChartAccountSummaryResponse(
                        UUID.randomUUID(),
                        "BJ",
                        "200000001",
                        "200000001",
                        "Membership Fees",
                        "Membership Fees",
                        CountryAccountGroup.MEMBERSHIP_FEES,
                        CountryChartAccountType.REVENUE,
                        "XOF",
                        BigDecimal.ZERO,
                        CountryChartAccountStatus.ACTIVE,
                        null,
                        null,
                        null
                ),
                new CountryChartAccountSummaryResponse(
                        UUID.randomUUID(),
                        "BJ",
                        "210000001",
                        "210000001",
                        "Commission Pool",
                        "Commission Pool",
                        CountryAccountGroup.COMMISSION_POOL,
                        CountryChartAccountType.LIABILITY,
                        "XOF",
                        BigDecimal.ZERO,
                        CountryChartAccountStatus.ACTIVE,
                        null,
                        null,
                        null
                )
        );
        when(countryChartOfAccountsService.autoGenerateRequiredAccounts("BJ")).thenReturn(accounts);

        ResponseEntity<CommonResponse<List<CountryChartAccountSummaryResponse>>> response =
                controller.autoGenerateCountryAccounts("BJ");

        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().data().size());
        assertEquals(1L, adminAuditLogRepository.countByEntityTypeAndEntityIdAndAction(
                "COUNTRY_ACCOUNT",
                "BJ",
                "AUTO_GENERATE_COUNTRY_CHART_ACCOUNTS"
        ));
    }
}
