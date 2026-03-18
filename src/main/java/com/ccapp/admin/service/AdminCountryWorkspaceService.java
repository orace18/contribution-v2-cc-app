package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminCountryWorkspaceAction;
import com.ccapp.admin.dto.AdminCountryWorkspaceActionResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceDetailResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceListItemResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceListResponse;
import com.ccapp.admin.dto.AdminCountryWorkspaceStatus;
import com.ccapp.admin.dto.AdminCountryWorkspaceTabResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.dto.CountryConfigReadinessSectionResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotItemResponse;
import com.ccapp.country.config.dto.CountryConfigSnapshotResponse;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminCountryWorkspaceService {

    private static final List<TabDefinition> TAB_DEFINITIONS = List.of(
            new TabDefinition("general", "General", CountryConfigType.GENERAL),
            new TabDefinition("accounting", "Accounting", CountryConfigType.ACCOUNTING),
            new TabDefinition("membership", "Membership", CountryConfigType.MEMBERSHIP),
            new TabDefinition("mlm", "MLM", CountryConfigType.MLM_LIMITS),
            new TabDefinition("financing", "Financing", CountryConfigType.FINANCING),
            new TabDefinition("payment", "Payment", CountryConfigType.PAYMENT),
            new TabDefinition("risk", "Risk", CountryConfigType.GUARANTEE)
    );

    private final CountryRepository countryRepository;
    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryConfigReadinessService countryConfigReadinessService;
    private final CountryConfigurationEngineService countryConfigurationEngineService;
    private final CountrySetupBootstrapService countrySetupBootstrapService;

    @Transactional(readOnly = true)
    public AdminCountryWorkspaceListResponse getCountriesOverview() {
        countrySetupBootstrapService.ensureMasterCountriesSeededIfEmpty();

        Set<String> initializedCountryCodes = countryConfigVersionRepository.findDistinctCountryCodes().stream()
                .map(this::normalizeCountryCode)
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        List<AdminCountryWorkspaceListItemResponse> configuredCountries = new java.util.ArrayList<>();
        List<AdminCountryWorkspaceListItemResponse> unconfiguredCountries = new java.util.ArrayList<>();

        for (Country country : countryRepository.findByActiveTrueOrderByNameAsc()) {
            String normalizedCountryCode = normalizeCountryCode(country.getCode());
            boolean initialized = initializedCountryCodes.contains(normalizedCountryCode);
            WorkspaceProgress progress = initialized
                    ? buildInitializedProgress(normalizedCountryCode)
                    : WorkspaceProgress.notConfigured();

            AdminCountryWorkspaceListItemResponse item = new AdminCountryWorkspaceListItemResponse(
                    normalizedCountryCode,
                    country.getName(),
                    progress.status(),
                    progress.readinessPercentage(),
                    detailPath(normalizedCountryCode),
                    buildPrimaryAction(progress.status(), normalizedCountryCode)
            );

            if (progress.status() == AdminCountryWorkspaceStatus.NOT_CONFIGURED) {
                unconfiguredCountries.add(item);
            } else {
                configuredCountries.add(item);
            }
        }

        return new AdminCountryWorkspaceListResponse(configuredCountries, unconfiguredCountries);
    }

    @Transactional(readOnly = true)
    public AdminCountryWorkspaceDetailResponse getCountryWorkspace(String countryCode) {
        countrySetupBootstrapService.ensureMasterCountriesSeededIfEmpty();

        Country country = resolveCountry(countryCode);
        String normalizedCountryCode = normalizeCountryCode(country.getCode());
        boolean initialized = countryConfigVersionRepository.findDistinctCountryCodes().stream()
                .map(this::normalizeCountryCode)
                .anyMatch(normalizedCountryCode::equals);

        WorkspaceProgress progress = initialized
                ? buildInitializedProgress(normalizedCountryCode)
                : WorkspaceProgress.notConfigured();

        CountryConfigSnapshotResponse snapshot = countryConfigurationEngineService.getCountryConfiguration(normalizedCountryCode, null);
        Map<CountryConfigType, CountryConfigSnapshotItemResponse> snapshotByType = snapshot.configs().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CountryConfigSnapshotItemResponse::configType,
                        item -> item,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<AdminCountryWorkspaceTabResponse> tabs = TAB_DEFINITIONS.stream()
                .map(tab -> toTabResponse(tab, progress.tabProgressByKey().get(tab.key()), snapshotByType.get(tab.configType())))
                .toList();

        return new AdminCountryWorkspaceDetailResponse(
                normalizedCountryCode,
                country.getName(),
                progress.status(),
                progress.readinessPercentage(),
                true,
                detailPath(normalizedCountryCode),
                setupPath(normalizedCountryCode),
                List.of(
                        new AdminCountryWorkspaceActionResponse(
                                AdminCountryWorkspaceAction.EDIT,
                                "Edit",
                                detailPath(normalizedCountryCode) + "?mode=edit"
                        ),
                        new AdminCountryWorkspaceActionResponse(
                                AdminCountryWorkspaceAction.CONTINUE_SETUP,
                                "Continue Setup",
                                setupPath(normalizedCountryCode)
                        )
                ),
                tabs
        );
    }

    private WorkspaceProgress buildInitializedProgress(String countryCode) {
        Map<String, CountryConfigReadinessSectionResponse> sectionByKey = countryConfigReadinessService.evaluateReadiness(countryCode)
                .sections()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        section -> normalizeSectionKey(section.section()),
                        section -> section,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        Map<String, TabProgress> tabProgressByKey = new LinkedHashMap<>();
        int totalChecks = 0;
        int passedChecks = 0;

        for (TabDefinition tab : TAB_DEFINITIONS) {
            CountryConfigReadinessSectionResponse section = sectionByKey.get(tab.key());
            int tabTotalChecks = section == null ? 0 : section.totalChecks();
            int tabPassedChecks = section == null ? 0 : section.passedChecks();
            int readinessPercentage = tabTotalChecks == 0
                    ? 0
                    : (int) Math.round((tabPassedChecks * 100.0d) / tabTotalChecks);
            AdminCountryWorkspaceStatus status = tabPassedChecks == 0
                    ? AdminCountryWorkspaceStatus.NOT_CONFIGURED
                    : (tabPassedChecks == tabTotalChecks
                    ? AdminCountryWorkspaceStatus.READY
                    : AdminCountryWorkspaceStatus.DRAFT);

            List<String> missingItems = section == null ? List.of() : section.missing();
            tabProgressByKey.put(tab.key(), new TabProgress(status, readinessPercentage, missingItems));

            totalChecks += tabTotalChecks;
            passedChecks += tabPassedChecks;
        }

        int readinessPercentage = totalChecks == 0
                ? 0
                : (int) Math.round((passedChecks * 100.0d) / totalChecks);
        AdminCountryWorkspaceStatus status = passedChecks == totalChecks && totalChecks > 0
                ? AdminCountryWorkspaceStatus.READY
                : AdminCountryWorkspaceStatus.DRAFT;
        return new WorkspaceProgress(status, readinessPercentage, tabProgressByKey);
    }

    private AdminCountryWorkspaceTabResponse toTabResponse(TabDefinition tab,
                                                           TabProgress progress,
                                                           CountryConfigSnapshotItemResponse snapshot) {
        TabProgress resolvedProgress = progress == null
                ? new TabProgress(AdminCountryWorkspaceStatus.NOT_CONFIGURED, 0, List.of())
                : progress;
        return new AdminCountryWorkspaceTabResponse(
                tab.key(),
                tab.label(),
                resolvedProgress.status(),
                resolvedProgress.readinessPercentage(),
                resolvedProgress.missingItems(),
                snapshot == null ? null : snapshot.version(),
                snapshot == null ? null : snapshot.effectiveDate(),
                snapshot == null ? null : snapshot.payload()
        );
    }

    private AdminCountryWorkspaceActionResponse buildPrimaryAction(AdminCountryWorkspaceStatus status, String countryCode) {
        if (status == AdminCountryWorkspaceStatus.READY) {
            return new AdminCountryWorkspaceActionResponse(
                    AdminCountryWorkspaceAction.VIEW,
                    "View",
                    detailPath(countryCode)
            );
        }
        if (status == AdminCountryWorkspaceStatus.DRAFT) {
            return new AdminCountryWorkspaceActionResponse(
                    AdminCountryWorkspaceAction.CONTINUE,
                    "Continue",
                    setupPath(countryCode)
            );
        }
        return new AdminCountryWorkspaceActionResponse(
                AdminCountryWorkspaceAction.CONFIGURE,
                "Configure",
                setupPath(countryCode)
        );
    }

    private Country resolveCountry(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        return countryRepository.findByCode(normalizedCountryCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "";
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSectionKey(String section) {
        if (section == null || section.isBlank()) {
            return "";
        }
        String normalized = section.trim().toLowerCase(Locale.ROOT);
        if ("risk_guarantee".equals(normalized)) {
            return "risk";
        }
        return normalized;
    }

    private String detailPath(String countryCode) {
        return "/countries/" + countryCode;
    }

    private String setupPath(String countryCode) {
        return detailPath(countryCode) + "/setup";
    }

    private record TabDefinition(String key, String label, CountryConfigType configType) {
    }

    private record TabProgress(
            AdminCountryWorkspaceStatus status,
            int readinessPercentage,
            List<String> missingItems
    ) {
    }

    private record WorkspaceProgress(
            AdminCountryWorkspaceStatus status,
            int readinessPercentage,
            Map<String, TabProgress> tabProgressByKey
    ) {
        private static WorkspaceProgress notConfigured() {
            Map<String, TabProgress> tabs = new LinkedHashMap<>();
            for (TabDefinition tab : TAB_DEFINITIONS) {
                tabs.put(tab.key(), new TabProgress(AdminCountryWorkspaceStatus.NOT_CONFIGURED, 0, List.of()));
            }
            return new WorkspaceProgress(AdminCountryWorkspaceStatus.NOT_CONFIGURED, 0, tabs);
        }
    }
}
