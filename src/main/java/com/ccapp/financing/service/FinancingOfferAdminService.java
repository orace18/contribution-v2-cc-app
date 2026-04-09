package com.ccapp.financing.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.financing.domain.entity.FinancialPartner;
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
import com.ccapp.country.repository.CountryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<FinancingOfferResponse> listOffers(UUID partnerId,
                                                           FinancingOfferStatus status,
                                                           String countryCode,
                                                           String search,
                                                           Integer page,
                                                           Integer size) {
        Pageable pageable = normalizePageable(page, size);
        String normalizedSearch = normalizeSearch(search);
        String normalizedCountryCode = normalizeNullableCountry(countryCode);
        Page<FinancingOffer> offerPage = financingOfferRepository.search(
                partnerId,
                status,
                normalizedCountryCode,
                normalizedSearch,
                pageable
        );
        Map<UUID, String> partnerNames = loadPartnerNames(offerPage.getContent());
        return new PageResponse<>(
                offerPage.getContent().stream()
                        .map(offer -> toResponse(offer, partnerNames.get(offer.getPartnerId())))
                        .toList(),
                offerPage.getNumber(),
                offerPage.getSize(),
                offerPage.getTotalElements(),
                offerPage.getTotalPages(),
                offerPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public FinancingOfferResponse getOffer(UUID offerId) {
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        return toResponse(offer, loadPartner(offer.getPartnerId()).getName());
    }

    @Transactional
    public FinancingOfferResponse createOffer(CreateFinancingOfferRequest request) {
        FinancialPartner partner = loadPartner(request.partnerId());
        String normalizedCountryCode = normalizeCountry(request.countryCode());
        validatePartnerCountry(partner, normalizedCountryCode);
        validateAmounts(request.minAmount(), request.maxAmount(), request.interestRate());
        String normalizedCurrency = normalizeCurrency(request.currency());

        FinancingOffer saved = financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(request.partnerId())
                .type(request.type())
                .name(normalizeName(request.name()))
                .description(normalizeNullable(request.description()))
                .countryCode(normalizedCountryCode)
                .minAmount(request.minAmount())
                .maxAmount(request.maxAmount())
                .interestRate(request.interestRate())
                .durationMonths(request.durationMonths())
                .currency(normalizedCurrency)
                .feesJson(normalizeJson(request.feesJson(), "feesJson"))
                .eligibilityRulesJson(normalizeJson(request.eligibilityRulesJson(), "eligibilityRulesJson"))
                .requiredDocumentsJson(normalizeJson(request.requiredDocumentsJson(), "requiredDocumentsJson"))
                .scoringRulesJson(normalizeJson(request.scoringRulesJson(), "scoringRulesJson"))
                .status(request.status() == null ? FinancingOfferStatus.ACTIVE : request.status())
                .build());

        syncCountryConfiguration(saved);
        upsertOfferRequirements(saved.getId(), request.requirements());
        return toResponse(saved, partner.getName());
    }

    @Transactional
    public FinancingOfferResponse updateOffer(UUID offerId, UpdateFinancingOfferRequest request) {
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        FinancialPartner partner = loadPartner(offer.getPartnerId());
        String normalizedCountryCode = normalizeCountry(request.countryCode());
        validatePartnerCountry(partner, normalizedCountryCode);
        validateAmounts(request.minAmount(), request.maxAmount(), request.interestRate());

        offer.setType(request.type());
        offer.setName(normalizeName(request.name()));
        offer.setDescription(normalizeNullable(request.description()));
        offer.setCountryCode(normalizedCountryCode);
        offer.setMinAmount(request.minAmount());
        offer.setMaxAmount(request.maxAmount());
        offer.setInterestRate(request.interestRate());
        offer.setDurationMonths(request.durationMonths());
        offer.setCurrency(normalizeCurrency(request.currency()));
        offer.setFeesJson(normalizeJson(request.feesJson(), "feesJson"));
        offer.setEligibilityRulesJson(normalizeJson(request.eligibilityRulesJson(), "eligibilityRulesJson"));
        offer.setRequiredDocumentsJson(normalizeJson(request.requiredDocumentsJson(), "requiredDocumentsJson"));
        offer.setScoringRulesJson(normalizeJson(request.scoringRulesJson(), "scoringRulesJson"));
        offer.setStatus(request.status());

        FinancingOffer saved = financingOfferRepository.save(offer);
        syncCountryConfiguration(saved);
        upsertOfferRequirements(saved.getId(), request.requirements());
        return toResponse(saved, partner.getName());
    }

    @Transactional
    public FinancingOfferResponse updateOfferStatus(UUID offerId, FinancingOfferStatus status) {
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        offer.setStatus(status);
        FinancingOffer saved = financingOfferRepository.save(offer);
        syncCountryConfiguration(saved);
        return toResponse(saved, loadPartner(saved.getPartnerId()).getName());
    }

    @Transactional
    public FinancingOfferResponse duplicateOffer(UUID offerId) {
        FinancingOffer source = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        FinancialPartner partner = loadPartner(source.getPartnerId());
        FinancingOffer duplicated = financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(source.getPartnerId())
                .type(source.getType())
                .name(buildDuplicateName(source.getName()))
                .description(source.getDescription())
                .countryCode(source.getCountryCode())
                .minAmount(source.getMinAmount())
                .maxAmount(source.getMaxAmount())
                .interestRate(source.getInterestRate())
                .durationMonths(source.getDurationMonths())
                .currency(source.getCurrency())
                .feesJson(source.getFeesJson())
                .eligibilityRulesJson(source.getEligibilityRulesJson())
                .requiredDocumentsJson(source.getRequiredDocumentsJson())
                .scoringRulesJson(source.getScoringRulesJson())
                .status(FinancingOfferStatus.INACTIVE)
                .build());
        syncCountryConfiguration(duplicated);

        List<OfferRequirementUpsertRequest> requirements = offerRequirementRepository
                .findByOfferIdOrderByRequirementTypeAsc(source.getId())
                .stream()
                .map(requirement -> new OfferRequirementUpsertRequest(
                        requirement.getRequirementType(),
                        requirement.isRequired(),
                        requirement.getVerificationType()
                ))
                .toList();
        upsertOfferRequirements(duplicated.getId(), requirements);
        return toResponse(duplicated, partner.getName());
    }

    @Transactional
    public List<OfferCountryConfigResponse> updateOfferCountries(UUID offerId, OfferCountryConfigUpdateRequest request) {
        if (request.countries().size() != 1) {
            throw new BusinessException("Offers are country-specific and must contain exactly one country configuration");
        }
        FinancingOffer offer = financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        OfferCountryConfigItemRequest countryItem = request.countries().get(0);
        offer.setCountryCode(normalizeCountry(countryItem.countryCode()));
        offer.setCurrency(normalizeCurrency(countryItem.currency()));
        offer.setStatus(countryItem.enabled() ? FinancingOfferStatus.ACTIVE : FinancingOfferStatus.INACTIVE);
        FinancingOffer saved = financingOfferRepository.save(offer);
        syncCountryConfiguration(saved);
        return offerCountryConfigRepository.findByOfferIdOrderByCountryCodeAsc(offerId).stream()
                .map(this::toCountryResponse)
                .toList();
    }

    private void syncCountryConfiguration(FinancingOffer offer) {
        offerCountryConfigRepository.deleteByOfferId(offer.getId());
        offerCountryConfigRepository.flush();
        OfferCountryConfig config = OfferCountryConfig.builder()
                .offerId(offer.getId())
                .countryCode(offer.getCountryCode())
                .enabled(offer.getStatus() == FinancingOfferStatus.ACTIVE)
                .currency(offer.getCurrency())
                .build();
        offerCountryConfigRepository.save(config);
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

    private FinancialPartner loadPartner(UUID partnerId) {
        return financialPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException("Financial partner not found"));
    }

    private Map<UUID, String> loadPartnerNames(List<FinancingOffer> offers) {
        return financialPartnerRepository.findAllById(
                        offers.stream().map(FinancingOffer::getPartnerId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(FinancialPartner::getId, FinancialPartner::getName));
    }

    private void validatePartnerCountry(FinancialPartner partner, String countryCode) {
        if (!partner.getCountryCode().equalsIgnoreCase(countryCode)) {
            throw new BusinessException("Offer country must match the selected partner country");
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

    private FinancingOfferResponse toResponse(FinancingOffer offer, String partnerName) {
        List<OfferCountryConfigResponse> countryConfigs = offerCountryConfigRepository.findByOfferIdOrderByCountryCodeAsc(offer.getId()).stream()
                .map(this::toCountryResponse)
                .toList();
        List<OfferRequirementResponse> requirements = offerRequirementRepository.findByOfferIdOrderByRequirementTypeAsc(offer.getId()).stream()
                .map(this::toRequirementResponse)
                .toList();

        return new FinancingOfferResponse(
                offer.getId(),
                offer.getPartnerId(),
                partnerName,
                offer.getType(),
                offer.getName(),
                offer.getDescription(),
                offer.getCountryCode(),
                offer.getMinAmount(),
                offer.getMaxAmount(),
                offer.getInterestRate(),
                offer.getDurationMonths(),
                offer.getCurrency(),
                offer.getFeesJson(),
                offer.getEligibilityRulesJson(),
                offer.getRequiredDocumentsJson(),
                offer.getScoringRulesJson(),
                offer.getStatus(),
                offer.getStatus() == FinancingOfferStatus.ACTIVE,
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

    private String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        ensureCountryExists(normalized);
        return normalized;
    }

    private String normalizeNullableCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return normalizeCountry(countryCode);
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

    private String normalizeJson(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessException(fieldName + " is required");
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(rawValue);
            if (!jsonNode.isObject() && !jsonNode.isArray()) {
                throw new BusinessException(fieldName + " must contain a JSON object or array");
            }
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(fieldName + " must contain valid JSON");
        }
    }

    private String buildDuplicateName(String name) {
        String normalized = normalizeName(name);
        return normalized.length() > 160
                ? normalized.substring(0, 160) + " Copy"
                : normalized + " Copy";
    }
}
