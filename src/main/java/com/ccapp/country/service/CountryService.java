package com.ccapp.country.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.domain.enums.CountryStatus;
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
    public List<CountryResponse> listConfiguredCountries() {
        return countryRepository.findByStatusOrderByNameAsc(CountryStatus.ACTIVE).stream()
                .map(country -> new CountryResponse(
                        country.getCode(),
                        country.getName(),
                        country.getCurrency(),
                        country.getStatus(),
                        country.isInternationalFallback()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Country resolveConfiguredOrFallback(String requestedCountryCode) {
        if (requestedCountryCode != null) {
            String normalized = requestedCountryCode.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                Country configured = countryRepository.findByCode(normalized).orElse(null);
                if (configured != null && configured.getStatus() == CountryStatus.ACTIVE) {
                    return configured;
                }
            }
        }
        return resolveFallbackCountry();
    }

    @Transactional(readOnly = true)
    public Country resolveByCodeOrThrow(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Account holder country is not set");
        }
        return countryRepository.findByCode(countryCode.trim().toUpperCase(Locale.ROOT))
                .filter(country -> country.getStatus() == CountryStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("Account holder country is not configured"));
    }

    private Country resolveFallbackCountry() {
        return countryRepository.findByInternationalFallbackTrue()
                .or(() -> countryRepository.findByCode(INTERNATIONAL_FALLBACK_CODE))
                .filter(country -> country.getStatus() == CountryStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("International fallback country not configured"));
    }
}
