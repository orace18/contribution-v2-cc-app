package com.ccapp.admin.service;

import com.ccapp.admin.domain.entity.AdminPolicy;
import com.ccapp.admin.domain.enums.AdminPolicyType;
import com.ccapp.admin.dto.*;
import com.ccapp.admin.repository.AdminPolicyRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.service.CountryConfigReadinessService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.service.SubscriptionPlanCatalogService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCountryConfigService {

    private final AdminPolicyRepository adminPolicyRepository;
    private final CountryRepository countryRepository;
    private final CountryConfigReadinessService countryConfigReadinessService;
    private final SubscriptionPlanCatalogService subscriptionPlanCatalogService;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public List<CountryConfigResponse> listCountries() {
        return countryRepository.findAllByOrderByCodeAsc().stream()
                .map(country -> new CountryConfigResponse(
                        country.getId(),
                        country.getCode(),
                        country.getName(),
                        country.getCurrency(),
                        country.isActive(),
                        country.isConfigured(),
                        country.isFallback(),
                        country.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public CountryConfigResponse saveCountry(CountryConfigRequest request) {
        String countryCode = request.countryCode().trim().toUpperCase(Locale.ROOT);

        Country country = countryRepository.findByCode(countryCode)
                .orElseGet(() -> Country.builder()
                        .id(UUID.nameUUIDFromBytes(("country:" + countryCode).getBytes(StandardCharsets.UTF_8)))
                        .code(countryCode)
                        .currency("USD")
                        .configured(false)
                        .fallback(false)
                        .build());

        if (country.isFallback() && !request.active()) {
            throw new BusinessException("Fallback country cannot be deactivated");
        }

        country.setName(request.name().trim());
        country.setActive(request.active() || country.isFallback());

        Country saved = countryRepository.save(country);
        return new CountryConfigResponse(
                saved.getId(),
                saved.getCode(),
                saved.getName(),
                saved.getCurrency(),
                saved.isActive(),
                saved.isConfigured(),
                saved.isFallback(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public CountryConfigResponse activateCountry(String code) {
        subscriptionService.assertCountryPlanCatalogValid(code);
        Country country = loadCountryByCode(code);
        country.setActive(true);
        Country saved = countryRepository.save(country);
        return toCountryResponse(saved);
    }

    @Transactional
    public CountryConfigResponse configureCountry(String code) {
        subscriptionService.assertCountryPlanCatalogValid(code);
        Country country = loadCountryByCode(code);
        country.setActive(true);
        boolean configured = countryConfigReadinessService.evaluateReadiness(country.getCode()).configured();
        country.setConfigured(configured);
        Country saved = countryRepository.save(country);
        return toCountryResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminSubscriptionPlanResponse> listSubscriptionPlans(String countryCode) {
        return subscriptionPlanCatalogService.listPlans(countryCode).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional
    public List<AdminSubscriptionPlanResponse> saveSubscriptionPlans(AdminSubscriptionPlanBulkUpsertRequest request) {
        String countryCode = request.countryCode().trim().toUpperCase(Locale.ROOT);
        return subscriptionPlanCatalogService.upsertPlans(countryCode, request.plans()).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional
    public AdminPolicyResponse createCreditPolicy(AdminPolicyRequest request) {
        return createPolicy(AdminPolicyType.CREDIT_POLICY, request);
    }

    @Transactional
    public AdminPolicyResponse createSeedCapitalPolicy(AdminPolicyRequest request) {
        return createPolicy(AdminPolicyType.SEED_CAPITAL_POLICY, request);
    }

    private AdminPolicyResponse createPolicy(AdminPolicyType type, AdminPolicyRequest request) {
        String countryCode = request.countryCode() == null || request.countryCode().isBlank()
                ? null
                : request.countryCode().trim().toUpperCase(Locale.ROOT);

        AdminPolicy saved = adminPolicyRepository.save(AdminPolicy.builder()
                .policyType(type)
                .countryCode(countryCode)
                .payload(request.payload().trim())
                .build());

        return new AdminPolicyResponse(
                saved.getId(),
                saved.getPolicyType(),
                saved.getCountryCode(),
                saved.getPayload(),
                saved.getCreatedAt()
        );
    }

    private AdminSubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        BigDecimal totalPrice = plan.getPrice() == null
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : plan.getPrice().setScale(4, RoundingMode.HALF_UP);
        BigDecimal monthlyPrice = plan.getDurationMonths() == null || plan.getDurationMonths() <= 0
                ? totalPrice
                : totalPrice.divide(BigDecimal.valueOf(plan.getDurationMonths()), 4, RoundingMode.HALF_UP);
        return new AdminSubscriptionPlanResponse(
                plan.getId(),
                plan.getCountry(),
                plan.getGrade(),
                plan.getDurationMonths(),
                monthlyPrice,
                totalPrice,
                plan.getCurrency(),
                plan.isActive(),
                plan.getCreatedAt()
        );
    }

    private Country loadCountryByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return countryRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("Country not found"));
    }

    private CountryConfigResponse toCountryResponse(Country country) {
        return new CountryConfigResponse(
                country.getId(),
                country.getCode(),
                country.getName(),
                country.getCurrency(),
                country.isActive(),
                country.isConfigured(),
                country.isFallback(),
                country.getCreatedAt()
        );
    }
}
