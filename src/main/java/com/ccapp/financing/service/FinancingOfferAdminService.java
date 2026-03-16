package com.ccapp.financing.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.OfferCountryConfig;
import com.ccapp.financing.domain.entity.OfferRequirement;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.dto.CreateFinancingOfferRequest;
import com.ccapp.financing.dto.FinancingOfferResponse;
import com.ccapp.financing.dto.OfferCountryConfigItemRequest;
import com.ccapp.financing.dto.OfferCountryConfigResponse;
import com.ccapp.financing.dto.OfferCountryConfigUpdateRequest;
import com.ccapp.financing.dto.OfferRequirementResponse;
import com.ccapp.financing.dto.OfferRequirementUpsertRequest;
import com.ccapp.financing.dto.UpdateFinancingOfferRequest;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.OfferCountryConfigRepository;
import com.ccapp.financing.repository.OfferRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancingOfferAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final FinancingOfferRepository financingOfferRepository;
    private final FinancialPartnerRepository financialPartnerRepository;
    private final OfferCountryConfigRepository offerCountryConfigRepository;
    private final OfferRequirementRepository offerRequirementRepository;
    private final CountryRepository countryRepository;

    @Transactional(readOnly = true)
    public PageResponse<FinancingOfferResponse> listOffers(UUID partnerId,
                                                           FinancingOfferStatus status,
                                                           String search,
                                                           Integer page,
                                                           Integer size) {
        Pageable pageable = normalizePageable(page, size);
        String normalizedSearch = normalizeSearch(search);
        Page<FinancingOffer> offerPage = financingOfferRepository.search(partnerId, status, normalizedSearch, pageable);
        Page<FinancingOfferResponse> responsePage = offerPage.map(this::toResponse);
        return PageResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public FinancingOfferResponse getOffer(UUID offerId) {
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        return toResponse(offer);
    }

    @Transactional
    public FinancingOfferResponse createOffer(CreateFinancingOfferRequest request) {
        validateFinancialPartnerExists(request.partnerId());
        validateAmounts(request.minAmount(), request.maxAmount(), request.interestRate());
        String normalizedCurrency = normalizeCurrency(request.currency());

        FinancingOffer saved = financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(request.partnerId())
                .name(normalizeName(request.name()))
                .description(normalizeNullable(request.description()))
                .loanType(normalizeLoanType(request.loanType()))
                .minAmount(request.minAmount())
                .maxAmount(request.maxAmount())
                .interestRate(request.interestRate())
                .durationMonths(request.durationMonths())
                .currency(normalizedCurrency)
                .status(request.status() == null ? FinancingOfferStatus.ACTIVE : request.status())
                .build());

        upsertOfferRequirements(saved.getId(), request.requirements());
        return toResponse(saved);
    }

    @Transactional
    public FinancingOfferResponse updateOffer(UUID offerId, UpdateFinancingOfferRequest request) {
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        validateAmounts(request.minAmount(), request.maxAmount(), request.interestRate());

        offer.setName(normalizeName(request.name()));
        offer.setDescription(normalizeNullable(request.description()));
        offer.setLoanType(normalizeLoanType(request.loanType()));
        offer.setMinAmount(request.minAmount());
        offer.setMaxAmount(request.maxAmount());
        offer.setInterestRate(request.interestRate());
        offer.setDurationMonths(request.durationMonths());
        offer.setCurrency(normalizeCurrency(request.currency()));
        offer.setStatus(request.status());

        FinancingOffer saved = financingOfferRepository.save(offer);
        upsertOfferRequirements(saved.getId(), request.requirements());
        return toResponse(saved);
    }

    @Transactional
    public List<OfferCountryConfigResponse> updateOfferCountries(UUID offerId, OfferCountryConfigUpdateRequest request) {
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));

        if (request.countries().isEmpty()) {
            throw new BusinessException("At least one country configuration is required");
        }

        List<OfferCountryConfig> existing = offerCountryConfigRepository.findByOfferIdOrderByCountryCodeAsc(offerId);
        var existingByCountry = existing.stream()
                .collect(Collectors.toMap(item -> item.getCountryCode().toUpperCase(Locale.ROOT), Function.identity()));

        List<OfferCountryConfig> toSave = new ArrayList<>();
        for (OfferCountryConfigItemRequest countryItem : request.countries()) {
            String countryCode = normalizeCountry(countryItem.countryCode());
            String currency = normalizeCurrency(countryItem.currency());
            ensureCountryExists(countryCode);

            OfferCountryConfig config = existingByCountry.get(countryCode);
            if (config == null) {
                config = OfferCountryConfig.builder()
                        .offerId(offer.getId())
                        .countryCode(countryCode)
                        .enabled(countryItem.enabled())
                        .currency(currency)
                        .build();
            } else {
                config.setEnabled(countryItem.enabled());
                config.setCurrency(currency);
            }
            toSave.add(config);
        }

        offerCountryConfigRepository.deleteByOfferId(offerId);
        return offerCountryConfigRepository.saveAll(toSave).stream()
                .map(this::toCountryResponse)
                .toList();
    }

    private void upsertOfferRequirements(UUID offerId, List<OfferRequirementUpsertRequest> requirements) {
        if (requirements == null) {
            return;
        }
        offerRequirementRepository.deleteByOfferId(offerId);
        if (requirements.isEmpty()) {
            return;
        }

        List<OfferRequirement> toSave = requirements.stream()
                .map(requirement -> OfferRequirement.builder()
                        .offerId(offerId)
                        .requirementType(requirement.requirementType())
                        .required(requirement.required())
                        .verificationType(requirement.verificationType())
                        .build())
                .toList();
        offerRequirementRepository.saveAll(toSave);
    }

    private void validateFinancialPartnerExists(UUID partnerId) {
        if (financialPartnerRepository.findById(partnerId).isEmpty()) {
            throw new BusinessException("Financial partner not found");
        }
    }

    private void validateAmounts(BigDecimal minAmount, BigDecimal maxAmount, BigDecimal interestRate) {
        if (minAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException("Offer minimum amount cannot be greater than maximum amount");
        }
        if (interestRate.signum() < 0) {
            throw new BusinessException("Interest rate must be positive");
        }
    }

    private FinancingOfferResponse toResponse(FinancingOffer offer) {
        List<OfferCountryConfigResponse> countryConfigs = offerCountryConfigRepository.findByOfferIdOrderByCountryCodeAsc(offer.getId()).stream()
                .map(this::toCountryResponse)
                .toList();
        List<OfferRequirementResponse> requirements = offerRequirementRepository.findByOfferIdOrderByRequirementTypeAsc(offer.getId()).stream()
                .map(this::toRequirementResponse)
                .toList();

        return new FinancingOfferResponse(
                offer.getId(),
                offer.getPartnerId(),
                offer.getName(),
                offer.getDescription(),
                offer.getLoanType(),
                offer.getMinAmount(),
                offer.getMaxAmount(),
                offer.getInterestRate(),
                offer.getDurationMonths(),
                offer.getCurrency(),
                offer.getStatus(),
                offer.getCreatedAt(),
                countryConfigs,
                requirements
        );
    }

    private OfferCountryConfigResponse toCountryResponse(OfferCountryConfig config) {
        return new OfferCountryConfigResponse(
                config.getId(),
                config.getOfferId(),
                config.getCountryCode(),
                config.isEnabled(),
                config.getCurrency(),
                config.getCreatedAt()
        );
    }

    private OfferRequirementResponse toRequirementResponse(OfferRequirement requirement) {
        return new OfferRequirementResponse(
                requirement.getId(),
                requirement.getOfferId(),
                requirement.getRequirementType(),
                requirement.isRequired(),
                requirement.getVerificationType(),
                requirement.getCreatedAt()
        );
    }

    private void ensureCountryExists(String countryCode) {
        if (countryRepository.findByCode(countryCode).isEmpty()) {
            throw new BusinessException("Country is not configured");
        }
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
            throw new BusinessException("Offer name is required");
        }
        return normalized;
    }

    private String normalizeLoanType(String loanType) {
        String normalized = loanType == null ? "" : loanType.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("Loan type is required");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
