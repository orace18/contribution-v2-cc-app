package com.ccapp.country.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.dto.CountryResponse;
import com.ccapp.country.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CountryService {

    private static final String INTERNATIONAL_FALLBACK_CODE = "INT";

    private final CountryRepository countryRepository;

    @Transactional(readOnly = true)
    public List<CountryResponse> listActiveCountries() {
        return countryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(country -> new CountryResponse(
                        country.getCode(),
                        country.getName(),
                        country.getCurrency(),
                        country.isActive(),
                        country.isConfigured(),
                        country.isFallback()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CountryResponse> listAllCountries() {
        return countryRepository.findAllByOrderByCodeAsc().stream()
                .map(country -> new CountryResponse(
                        country.getCode(),
                        country.getName(),
                        country.getCurrency(),
                        country.isActive(),
                        country.isConfigured(),
                        country.isFallback()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Country resolveByCodeOrThrow(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Account holder country is not set");
        }
        return countryRepository.findByCode(normalize(countryCode))
                .filter(Country::isActive)
                .orElseThrow(() -> new BusinessException("Account holder country is not configured"));
    }

    @Transactional(readOnly = true)
    public Country resolveSelectedCountryOrThrow(String requestedCountryCode) {
        if (requestedCountryCode == null || requestedCountryCode.isBlank()) {
            throw new BusinessException("Country is required");
        }
        return countryRepository.findByCode(normalize(requestedCountryCode))
                .filter(Country::isActive)
                .orElseThrow(() -> new BusinessException("Selected country is not active"));
    }

    @Transactional(readOnly = true)
    public Country resolveServiceCountryForSelection(String selectedCountryCode) {
        Country selected = resolveSelectedCountryOrThrow(selectedCountryCode);
        if (selected.isConfigured()) {
            return selected;
        }
        return resolveFallbackCountry();
    }

    @Transactional(readOnly = true)
    public Country resolveServiceCountryByCodeOrFallback(String serviceCountryCode) {
        if (serviceCountryCode != null && !serviceCountryCode.isBlank()) {
            Country country = countryRepository.findByCode(normalize(serviceCountryCode)).orElse(null);
            if (country != null && country.isActive()) {
                return country;
            }
        }
        return resolveFallbackCountry();
    }

    @Transactional(readOnly = true)
    public Country resolveFallbackCountry() {
        return countryRepository.findByFallbackTrue()
                .or(() -> countryRepository.findByCode(INTERNATIONAL_FALLBACK_CODE))
                .filter(Country::isActive)
                .orElseThrow(() -> new BusinessException("International fallback country not configured"));
    }

    private String normalize(String countryCode) {
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
