package com.ccapp.admin.service;

import com.ccapp.admin.dto.LookupItemResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.dto.CountryConfigFullResponse;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.config.service.CountryConfigurationEngineService;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.OfferCountryConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminLookupService {

    private final CountryRepository countryRepository;
    private final FinancialPartnerRepository financialPartnerRepository;
    private final FinancingOfferRepository financingOfferRepository;
    private final OfferCountryConfigRepository offerCountryConfigRepository;
    private final CountryChartAccountRepository countryChartAccountRepository;
    private final CountryConfigVersionRepository countryConfigVersionRepository;
    private final CountryConfigurationEngineService countryConfigurationEngineService;
    private final CountrySetupBootstrapService countrySetupBootstrapService;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<LookupItemResponse> listCountries() {
        return listWorldCountries();
    }

    @Transactional
    public List<LookupItemResponse> listWorldCountries() {
        countrySetupBootstrapService.ensureMasterCountriesSeededIfEmpty();
        long totalCountriesInDb = countryRepository.count();
        List<Country> activeCountries = countryRepository.findByActiveTrueOrderByNameAsc();
        Set<String> initializedCountryCodes = countryConfigVersionRepository.findDistinctCountryCodes().stream()
                .map(code -> code == null ? null : code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        long configuredCount = activeCountries.stream()
                .map(country -> country.getCode() == null ? "" : country.getCode().trim().toUpperCase(Locale.ROOT))
                .filter(initializedCountryCodes::contains)
                .count();
        long unconfiguredCount = activeCountries.size() - configuredCount;

        log.info(
                "World country lookup counts: totalCountriesInDb={}, activeCountriesCount={}, configuredCountriesCount={}, unconfiguredCountriesCount={}",
                totalCountriesInDb,
                activeCountries.size(),
                configuredCount,
                unconfiguredCount
        );

        if (activeCountries.isEmpty()) {
            throw new BusinessException("No active countries available");
        }

        log.info("World countries count = {}", activeCountries.size());

        return activeCountries.stream()
                .map(country -> new LookupItemResponse(
                        country.getCode(),
                        country.getName(),
                        country.getCurrency()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LookupItemResponse> listPartners(String countryCode) {
        String normalizedCountryCode = countryCode == null || countryCode.isBlank()
                ? null
                : normalizeCountryCode(countryCode);
        if (normalizedCountryCode == null) {
            return financialPartnerRepository.findByStatusOrderByNameAsc(FinancialPartnerStatus.ACTIVE)
                    .stream()
                    .map(partner -> new LookupItemResponse(
                            partner.getId().toString(),
                            partner.getName(),
                            partner.getCountryCode() + " • " + partner.getType().name()
                    ))
                    .toList();
        }
        return financialPartnerRepository.findByCountryCodeOrderByNameAsc(normalizedCountryCode).stream()
                .filter(partner -> partner.getStatus() == FinancialPartnerStatus.ACTIVE)
                .map(partner -> new LookupItemResponse(
                        partner.getId().toString(),
                        partner.getName(),
                        partner.getCountryCode() + " • " + partner.getType().name()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LookupItemResponse> listOffers(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        Set<UUID> enabledOfferIds = offerCountryConfigRepository
                .findByCountryCodeAndEnabledTrueOrderByCreatedAtDesc(normalizedCountryCode)
                .stream()
                .map(config -> config.getOfferId())
                .collect(java.util.stream.Collectors.toSet());

        Map<UUID, String> partnerNameById = new HashMap<>();
        financialPartnerRepository.findAll().forEach(partner -> partnerNameById.put(partner.getId(), partner.getName()));

        return financingOfferRepository.findByStatusOrderByCreatedAtDesc(FinancingOfferStatus.ACTIVE)
                .stream()
                .filter(offer -> enabledOfferIds.contains(offer.getId()))
                .map(offer -> {
                    String partnerName = partnerNameById.getOrDefault(offer.getPartnerId(), "Unknown partner");
                    return new LookupItemResponse(
                            offer.getId().toString(),
                            offer.getName(),
                            partnerName + " • " + offer.getCurrency()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LookupItemResponse> listAccounts(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        return countryChartAccountRepository.findByCountryCodeOrderByAccountCodeAsc(normalizedCountryCode)
                .stream()
                .map(account -> new LookupItemResponse(
                        account.getId().toString(),
                        account.getAccountCode() + " - " + account.getAccountName(),
                        account.getAccountType().name() + " • " + account.getStatus().name()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LookupItemResponse> listPsps(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);

        CountryConfigFullResponse fullConfig = countryConfigurationEngineService
                .getFullCountryConfiguration(normalizedCountryCode, OffsetDateTime.now());
        ObjectNode payment = fullConfig.payment() != null && fullConfig.payment().isObject()
                ? (ObjectNode) fullConfig.payment()
                : objectMapper.createObjectNode();

        Map<String, LookupItemResponse> deduplicated = new LinkedHashMap<>();
        JsonNode activePsps = payment.get("activePsps");
        if (activePsps != null && activePsps.isArray()) {
            activePsps.forEach(item -> {
                if (!item.isTextual() || item.asText().isBlank()) {
                    return;
                }
                String provider = item.asText().trim();
                deduplicated.putIfAbsent(provider.toUpperCase(Locale.ROOT),
                        new LookupItemResponse(provider, provider, "ACTIVE"));
            });
        }

        JsonNode pspsNode = payment.get("psps");
        if (pspsNode != null && pspsNode.isArray()) {
            pspsNode.forEach(item -> {
                if (!item.isObject()) {
                    return;
                }
                String provider = readText(item, "id", "provider", "name");
                if (provider == null) {
                    return;
                }
                String label = readText(item, "name", "label", "provider");
                String status = readText(item, "status");
                deduplicated.putIfAbsent(provider.toUpperCase(Locale.ROOT),
                        new LookupItemResponse(provider, label == null ? provider : label, status == null ? "ACTIVE" : status));
            });
        }

        JsonNode singlePsp = payment.get("psp");
        if (singlePsp != null && singlePsp.isObject()) {
            String provider = readText(singlePsp, "provider", "id", "name");
            if (provider != null) {
                deduplicated.putIfAbsent(provider.toUpperCase(Locale.ROOT),
                        new LookupItemResponse(provider, provider, "ACTIVE"));
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private void assertCountryExists(String countryCode) {
        countryRepository.findByCode(countryCode).orElseThrow(() -> new BusinessException("Country not found"));
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("countryCode is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String readText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode candidate = node.get(field);
            if (candidate != null && candidate.isTextual() && !candidate.asText().isBlank()) {
                return candidate.asText().trim();
            }
        }
        return null;
    }
}
