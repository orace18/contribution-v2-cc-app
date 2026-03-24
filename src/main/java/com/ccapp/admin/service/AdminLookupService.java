package com.ccapp.admin.service;

import com.ccapp.admin.dto.LookupItemResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.config.repository.CountryConfigVersionRepository;
import com.ccapp.country.config.service.CountrySetupBootstrapService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.OfferCountryConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    private final CountrySetupBootstrapService countrySetupBootstrapService;

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
                            partner.getCountryCode() + " - " + partner.getType().name()
                    ))
                    .toList();
        }
        return financialPartnerRepository.findByCountryCodeOrderByNameAsc(normalizedCountryCode).stream()
                .filter(partner -> partner.getStatus() == FinancialPartnerStatus.ACTIVE)
                .map(partner -> new LookupItemResponse(
                        partner.getId().toString(),
                        partner.getName(),
                        partner.getCountryCode() + " - " + partner.getType().name()
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
                .collect(Collectors.toSet());

        Map<UUID, String> partnerNameById = new HashMap<>();
        financialPartnerRepository.findAll().forEach(partner -> partnerNameById.put(partner.getId(), partner.getName()));

        return financingOfferRepository.findByStatusOrderByCreatedAtDesc(FinancingOfferStatus.ACTIVE)
                .stream()
                .filter(offer -> matchesCountry(offer, normalizedCountryCode, enabledOfferIds))
                .map(offer -> {
                    String partnerName = partnerNameById.getOrDefault(offer.getPartnerId(), "Unknown partner");
                    return new LookupItemResponse(
                            offer.getId().toString(),
                            offer.getName(),
                            partnerName + " - " + offer.getCurrency()
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
                        account.getAccountCode(),
                        account.getAccountCode() + " - " + account.getAccountName(),
                        account.getAccountType().name() + " - " + account.getStatus().name()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LookupItemResponse> listPsps(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        assertCountryExists(normalizedCountryCode);
        return List.of(new LookupItemResponse("JAWUPAY", "JawuPay", "Single PSP managed centrally"));
    }

    private boolean matchesCountry(FinancingOffer offer, String countryCode, Set<UUID> enabledOfferIds) {
        return countryCode.equalsIgnoreCase(offer.getCountryCode()) || enabledOfferIds.contains(offer.getId());
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
}
