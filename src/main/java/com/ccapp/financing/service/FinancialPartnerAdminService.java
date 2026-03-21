package com.ccapp.financing.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.dto.CreateFinancialPartnerRequest;
import com.ccapp.financing.dto.FinancialPartnerResponse;
import com.ccapp.financing.dto.UpdateFinancialPartnerRequest;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialPartnerAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final FinancialPartnerRepository financialPartnerRepository;
    private final CountryRepository countryRepository;

    @Transactional(readOnly = true)
    public PageResponse<FinancialPartnerResponse> listPartners(FinancialPartnerStatus status,
                                                               FinancialPartnerType type,
                                                               String countryCode,
                                                               String search,
                                                               Integer page,
                                                               Integer size) {
        Pageable pageable = normalizePageable(page, size);
        String normalizedCountryCode = normalizeNullableCountry(countryCode);
        String normalizedSearch = normalizeSearch(search);
        Page<FinancialPartner> partnerPage = financialPartnerRepository.search(
                status,
                type,
                normalizedCountryCode,
                normalizedSearch,
                pageable
        );
        return PageResponse.from(partnerPage.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public FinancialPartnerResponse getPartner(UUID partnerId) {
        FinancialPartner partner = financialPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Financial partner not found"));
        return toResponse(partner);
    }

    @Transactional
    public FinancialPartnerResponse createPartner(CreateFinancialPartnerRequest request) {
        String normalizedCountryCode = normalizeRequiredCountry(request.countryCode());
        ensureCountryExists(normalizedCountryCode);
        String normalizedName = normalizeName(request.name());
        financialPartnerRepository.findByNameIgnoreCaseAndCountryCodeIgnoreCase(normalizedName, normalizedCountryCode)
                .ifPresent(existing -> {
                    throw new BusinessException("Partner already exists for this country");
                });

        FinancialPartner saved = financialPartnerRepository.save(FinancialPartner.builder()
                .name(normalizedName)
                .type(request.type())
                .countryCode(normalizedCountryCode)
                .currency(normalizeCurrency(request.currency()))
                .contactEmail(normalizeNullableEmail(request.contactEmail()))
                .description(normalizeNullableDescription(request.description()))
                .status(request.status() == null ? FinancialPartnerStatus.ACTIVE : request.status())
                .apiEndpoint(normalizeNullableEndpoint(request.apiEndpoint()))
                .build());
        return toResponse(saved);
    }

    @Transactional
    public FinancialPartnerResponse updatePartner(UUID partnerId, UpdateFinancialPartnerRequest request) {
        FinancialPartner partner = financialPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Financial partner not found"));
        String normalizedCountryCode = normalizeRequiredCountry(request.countryCode());
        ensureCountryExists(normalizedCountryCode);
        String normalizedName = normalizeName(request.name());

        financialPartnerRepository.findByNameIgnoreCaseAndCountryCodeIgnoreCase(normalizedName, normalizedCountryCode)
                .filter(existing -> !existing.getId().equals(partnerId))
                .ifPresent(existing -> {
                    throw new BusinessException("Partner already exists for this country");
                });

        partner.setName(normalizedName);
        partner.setType(request.type());
        partner.setCountryCode(normalizedCountryCode);
        partner.setCurrency(normalizeCurrency(request.currency()));
        partner.setContactEmail(normalizeNullableEmail(request.contactEmail()));
        partner.setDescription(normalizeNullableDescription(request.description()));
        partner.setStatus(request.status());
        partner.setApiEndpoint(normalizeNullableEndpoint(request.apiEndpoint()));
        return toResponse(financialPartnerRepository.save(partner));
    }

    private void ensureCountryExists(String countryCode) {
        if (countryRepository.findByCode(countryCode).isEmpty()) {
            throw new BusinessException("Country is not configured");
        }
    }

    private FinancialPartnerResponse toResponse(FinancialPartner partner) {
        return new FinancialPartnerResponse(
                partner.getId(),
                partner.getName(),
                partner.getType(),
                partner.getCountryCode(),
                partner.getCurrency(),
                partner.getContactEmail(),
                partner.getDescription(),
                partner.getStatus(),
                partner.getApiEndpoint(),
                partner.getCreatedAt()
        );
    }

    private Pageable normalizePageable(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("Partner name is required");
        }
        return normalized;
    }

    private String normalizeRequiredCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableEndpoint(String apiEndpoint) {
        if (apiEndpoint == null || apiEndpoint.isBlank()) {
            return null;
        }
        return apiEndpoint.trim();
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Partner currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableEmail(String contactEmail) {
        if (contactEmail == null || contactEmail.isBlank()) {
            return null;
        }
        return contactEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
