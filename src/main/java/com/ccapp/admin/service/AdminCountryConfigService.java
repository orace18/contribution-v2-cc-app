package com.ccapp.admin.service;

import com.ccapp.admin.domain.entity.AdminCountry;
import com.ccapp.admin.domain.entity.AdminPolicy;
import com.ccapp.admin.domain.enums.AdminPolicyType;
import com.ccapp.admin.dto.*;
import com.ccapp.admin.repository.AdminCountryRepository;
import com.ccapp.admin.repository.AdminPolicyRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminCountryConfigService {

    private final AdminCountryRepository adminCountryRepository;
    private final AdminPolicyRepository adminPolicyRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional(readOnly = true)
    public List<CountryConfigResponse> listCountries() {
        return adminCountryRepository.findAllByOrderByCountryCodeAsc().stream()
                .map(country -> new CountryConfigResponse(
                        country.getId(),
                        country.getCountryCode(),
                        country.getName(),
                        country.isActive(),
                        country.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public CountryConfigResponse saveCountry(CountryConfigRequest request) {
        String countryCode = request.countryCode().trim().toUpperCase(Locale.ROOT);

        AdminCountry country = adminCountryRepository.findByCountryCode(countryCode)
                .orElseGet(() -> AdminCountry.builder()
                        .countryCode(countryCode)
                        .build());

        country.setName(request.name().trim());
        country.setActive(request.active());

        AdminCountry saved = adminCountryRepository.save(country);
        return new CountryConfigResponse(
                saved.getId(),
                saved.getCountryCode(),
                saved.getName(),
                saved.isActive(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<AdminSubscriptionPlanResponse> listSubscriptionPlans() {
        return subscriptionPlanRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional
    public AdminSubscriptionPlanResponse createSubscriptionPlan(AdminSubscriptionPlanRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String country = request.country().trim().toUpperCase(Locale.ROOT);
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);

        boolean duplicate = subscriptionPlanRepository.findAll().stream()
                .anyMatch(plan -> plan.getCode().equalsIgnoreCase(code)
                        && plan.getCountry().equalsIgnoreCase(country)
                        && plan.getCurrency().equalsIgnoreCase(currency));
        if (duplicate) {
            throw new BusinessException("Subscription plan already exists for code/country/currency");
        }

        SubscriptionPlan saved = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .code(code)
                .name(request.name().trim())
                .country(country)
                .currency(currency)
                .price(request.price())
                .active(request.active())
                .build());

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
                plan.getCode(),
                plan.getName(),
                plan.getCountry(),
                plan.getCurrency(),
                plan.getPrice(),
                plan.isActive(),
                plan.getCreatedAt()
        );
    }
}
