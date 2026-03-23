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
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCountryConfigService {

    private final AdminPolicyRepository adminPolicyRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CountryRepository countryRepository;
    private final CountryConfigReadinessService countryConfigReadinessService;
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
    public List<AdminSubscriptionPlanResponse> listSubscriptionPlans() {
        return subscriptionPlanRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional
    public AdminSubscriptionPlanResponse createSubscriptionPlan(AdminSubscriptionPlanRequest request) {
        String code = resolveCode(request);
        String name = resolveName(request);
        String country = request.country().trim().toUpperCase(Locale.ROOT);
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        validateDuration(request.grade(), request.durationMonths());

        boolean duplicate = subscriptionPlanRepository.findAll().stream()
                .anyMatch(plan -> plan.getCode().equalsIgnoreCase(code)
                        && plan.getCountry().equalsIgnoreCase(country)
                        && plan.getCurrency().equalsIgnoreCase(currency));
        if (duplicate) {
            throw new BusinessException("Subscription plan already exists for code/country/currency");
        }
        if (subscriptionPlanRepository.existsByCountryAndGradeAndDurationMonthsAndCurrency(
                country,
                request.grade(),
                request.durationMonths(),
                currency
        )) {
            throw new BusinessException("Subscription plan already exists for grade/duration/country/currency");
        }

        SubscriptionPlan saved = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .code(code)
                .name(name)
                .grade(request.grade())
                .durationMonths(request.durationMonths())
                .country(country)
                .currency(currency)
                .price(request.unitAmount())
                .active(request.active())
                .build());
        ensureCountryExists(country, currency);

        return toPlanResponse(saved);
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
        return new AdminSubscriptionPlanResponse(
                plan.getId(),
                plan.getCountry(),
                plan.getGrade(),
                plan.getDurationMonths(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getCode(),
                plan.getName(),
                plan.isActive(),
                plan.getCreatedAt()
        );
    }

    private void validateDuration(SubscriptionGrade grade, Integer durationMonths) {
        if (durationMonths == null || durationMonths <= 0) {
            throw new BusinessException("durationMonths must be positive");
        }
        if (grade != SubscriptionGrade.FREE && !List.of(1, 3, 12).contains(durationMonths)) {
            throw new BusinessException("durationMonths must be one of 1, 3 or 12 for paid plans");
        }
    }

    private String resolveCode(AdminSubscriptionPlanRequest request) {
        if (request.code() != null && !request.code().isBlank()) {
            return request.code().trim().toUpperCase(Locale.ROOT);
        }
        return request.grade().name() + "_" + request.durationMonths() + "M";
    }

    private String resolveName(AdminSubscriptionPlanRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            return request.name().trim();
        }
        return request.grade().name() + " " + request.durationMonths() + " months";
    }

    private void ensureCountryExists(String code, String preferredCurrency) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        countryRepository.findByCode(normalizedCode).orElseGet(() -> countryRepository.save(Country.builder()
                .id(UUID.nameUUIDFromBytes(("country:" + normalizedCode).getBytes(StandardCharsets.UTF_8)))
                .code(normalizedCode)
                .name(normalizedCode)
                .currency(preferredCurrency == null || preferredCurrency.isBlank() ? "USD" : preferredCurrency.trim().toUpperCase(Locale.ROOT))
                .active(true)
                .configured(false)
                .fallback(false)
                .build()));
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
