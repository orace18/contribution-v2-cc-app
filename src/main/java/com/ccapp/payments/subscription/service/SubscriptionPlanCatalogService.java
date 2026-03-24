package com.ccapp.payments.subscription.service;

import com.ccapp.admin.dto.AdminSubscriptionPlanRequest;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanCatalogService {

    private static final List<SubscriptionGrade> REQUIRED_PAID_FORMULAS = List.of(
            SubscriptionGrade.CLASSIC,
            SubscriptionGrade.GOLD,
            SubscriptionGrade.DIAMOND
    );
    private static final Set<Integer> REQUIRED_PAID_DURATIONS = Set.of(1, 3, 12);
    private static final int CANONICAL_FREE_DURATION_MONTHS = 1;

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CountryRepository countryRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> listPlans(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return subscriptionPlanRepository.findAllByOrderByCreatedAtDesc();
        }
        return subscriptionPlanRepository.findAllByCountryOrderByGradeAscDurationMonthsAscPriceAsc(
                normalizeCountryCode(countryCode)
        );
    }

    @Transactional
    public List<SubscriptionPlan> upsertPlans(String countryCode, List<AdminSubscriptionPlanRequest> requests) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        List<CatalogPlanInput> plans = normalizeRequests(normalizedCountryCode, requests);
        ensureCountryExists(normalizedCountryCode, plans.get(0).currency());
        return applyCatalog(normalizedCountryCode, plans);
    }

    public void assertMembershipPayloadCatalogValid(JsonNode membershipPayload) {
        normalizeMembershipPayload(membershipPayload);
    }

    public void assertMembershipPayloadCompatibleWithCountry(String countryCode, JsonNode membershipPayload) {
        List<CatalogPlanInput> plans = normalizeMembershipPayload(membershipPayload);
        validateCountryCurrency(normalizeCountryCode(countryCode), plans);
    }

    @Transactional
    public List<SubscriptionPlan> syncCountryCatalogFromMembershipConfig(String countryCode, JsonNode membershipPayload) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        List<CatalogPlanInput> plans = normalizeMembershipPayload(membershipPayload);
        validateCountryCurrency(normalizedCountryCode, plans);
        ensureCountryExists(normalizedCountryCode, plans.get(0).currency());
        return applyCatalog(normalizedCountryCode, plans);
    }

    private List<CatalogPlanInput> normalizeRequests(String countryCode, List<AdminSubscriptionPlanRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("Subscription plans must define 10 plans: 9 paid variants and 1 FREE plan");
        }

        List<CatalogPlanInput> plans = requests.stream()
                .map(request -> normalizeRequest(countryCode, request))
                .toList();
        validateCanonicalCatalog(plans);
        validateCountryCurrency(countryCode, plans);
        return plans;
    }

    private CatalogPlanInput normalizeRequest(String countryCode, AdminSubscriptionPlanRequest request) {
        if (request == null) {
            throw new BusinessException("Subscription plan request is required");
        }

        String requestCountryCode = request.countryCode() == null || request.countryCode().isBlank()
                ? countryCode
                : normalizeCountryCode(request.countryCode());
        if (!requestCountryCode.equals(countryCode)) {
            throw new BusinessException("All subscription plans in the request must use the same countryCode");
        }

        SubscriptionGrade formulaType = request.formulaType();
        if (formulaType == null) {
            throw new BusinessException("formulaType is required");
        }

        Integer durationMonths = request.durationMonths();
        if (durationMonths == null || durationMonths <= 0) {
            throw new BusinessException("durationMonths must be positive");
        }

        String currency = normalizeCurrency(request.currency());
        BigDecimal normalizedMonthlyPrice = normalizeAmount(request.monthlyPrice());
        BigDecimal totalPrice = resolveTotalPrice(
                formulaType,
                durationMonths,
                normalizedMonthlyPrice,
                request.totalPrice()
        );

        return new CatalogPlanInput(
                formulaType,
                durationMonths,
                normalizedMonthlyPrice,
                totalPrice,
                currency,
                request.isActive()
        );
    }

    private List<CatalogPlanInput> normalizeMembershipPayload(JsonNode membershipPayload) {
        if (membershipPayload == null || membershipPayload.isNull() || !membershipPayload.isObject()) {
            throw new BusinessException("membership payload must be a JSON object");
        }

        JsonNode plansNode = membershipPayload.get("plans");
        if (plansNode == null || !plansNode.isArray() || plansNode.isEmpty()) {
            throw new BusinessException("membership.plans must define 10 plans: 9 paid variants and 1 FREE plan");
        }

        List<CatalogPlanInput> plans = new ArrayList<>();
        plansNode.forEach(planNode -> plans.add(normalizeMembershipPlanNode(planNode)));
        validateCanonicalCatalog(plans);
        return plans;
    }

    private CatalogPlanInput normalizeMembershipPlanNode(JsonNode planNode) {
        if (!planNode.isObject()) {
            throw new BusinessException("membership.plans entries must be objects");
        }

        ObjectNode plan = (ObjectNode) planNode;
        SubscriptionGrade formulaType = resolveFormulaType(plan);
        int durationMonths = resolveDurationMonths(plan);
        BigDecimal monthlyPrice = normalizeAmount(optionalDecimalField(plan, "monthlyPrice"));
        BigDecimal totalPrice = resolvePlanTotalPrice(formulaType, durationMonths, plan, monthlyPrice);
        String currency = resolvePlanCurrency(plan);
        boolean active = resolvePlanActive(plan);

        return new CatalogPlanInput(
                formulaType,
                durationMonths,
                monthlyPrice,
                totalPrice,
                currency,
                active
        );
    }

    private List<SubscriptionPlan> applyCatalog(String countryCode, List<CatalogPlanInput> plans) {
        Map<String, SubscriptionPlan> existingByKey = new LinkedHashMap<>();
        subscriptionPlanRepository.findAllByCountryOrderByGradeAscDurationMonthsAscPriceAsc(countryCode)
                .forEach(plan -> existingByKey.put(keyOf(plan.getGrade(), plan.getDurationMonths(), plan.getCurrency()), plan));

        List<SubscriptionPlan> toSave = new ArrayList<>();
        for (CatalogPlanInput input : plans) {
            String key = keyOf(input.formulaType(), input.durationMonths(), input.currency());
            SubscriptionPlan plan = existingByKey.remove(key);
            if (plan == null) {
                plan = new SubscriptionPlan();
            }
            plan.setCode(resolveCode(input));
            plan.setName(resolveName(input));
            plan.setGrade(input.formulaType());
            plan.setDurationMonths(input.durationMonths());
            plan.setCountry(countryCode);
            plan.setCurrency(input.currency());
            plan.setPrice(input.totalPrice());
            plan.setActive(input.isActive());
            toSave.add(plan);
        }

        existingByKey.values().forEach(plan -> {
            if (plan.isActive()) {
                plan.setActive(false);
                toSave.add(plan);
            }
        });

        return subscriptionPlanRepository.saveAll(toSave).stream()
                .sorted(Comparator
                        .comparing(SubscriptionPlan::getGrade)
                        .thenComparing(SubscriptionPlan::getDurationMonths)
                        .thenComparing(SubscriptionPlan::getPrice))
                .toList();
    }

    private void validateCanonicalCatalog(List<CatalogPlanInput> plans) {
        if (plans.size() != 10) {
            throw new BusinessException("Subscription plans must define exactly 10 plans: 9 paid variants and 1 FREE plan");
        }

        Set<String> seenKeys = new HashSet<>();
        Set<String> currencies = new HashSet<>();
        Map<SubscriptionGrade, Set<Integer>> durationsByFormula = new EnumMap<>(SubscriptionGrade.class);
        for (SubscriptionGrade grade : SubscriptionGrade.values()) {
            durationsByFormula.put(grade, new HashSet<>());
        }

        int freePlanCount = 0;
        for (CatalogPlanInput plan : plans) {
            if (!plan.isActive()) {
                throw new BusinessException("All required subscription plans must be active");
            }

            currencies.add(plan.currency());
            String key = keyOf(plan.formulaType(), plan.durationMonths(), plan.currency());
            if (!seenKeys.add(key)) {
                throw new BusinessException("Subscription plans contain duplicate formulaType/durationMonths combinations");
            }

            if (plan.formulaType() == SubscriptionGrade.FREE) {
                freePlanCount += 1;
                if (plan.durationMonths() != CANONICAL_FREE_DURATION_MONTHS) {
                    throw new BusinessException("FREE plan must use durationMonths = 1");
                }
                if (plan.totalPrice().compareTo(BigDecimal.ZERO) != 0) {
                    throw new BusinessException("FREE plan totalPrice must be 0");
                }
                if (plan.monthlyPrice() != null && plan.monthlyPrice().compareTo(BigDecimal.ZERO) != 0) {
                    throw new BusinessException("FREE plan monthlyPrice must be 0");
                }
                durationsByFormula.get(SubscriptionGrade.FREE).add(plan.durationMonths());
                continue;
            }

            if (!REQUIRED_PAID_DURATIONS.contains(plan.durationMonths())) {
                throw new BusinessException("durationMonths must be one of 1, 3 or 12 for paid plans");
            }
            if (plan.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Paid plans totalPrice must be positive");
            }

            BigDecimal expectedMonthlyPrice = normalizeAmount(
                    plan.totalPrice().divide(BigDecimal.valueOf(plan.durationMonths()), 4, RoundingMode.HALF_UP)
            );
            if (plan.monthlyPrice() != null && plan.monthlyPrice().compareTo(expectedMonthlyPrice) != 0) {
                throw new BusinessException("monthlyPrice must equal totalPrice divided by durationMonths");
            }

            durationsByFormula.get(plan.formulaType()).add(plan.durationMonths());
        }

        if (currencies.size() != 1) {
            throw new BusinessException("All subscription plans for a country must use the same currency");
        }
        if (freePlanCount != 1) {
            throw new BusinessException("Subscription plans must include exactly one FREE plan");
        }

        for (SubscriptionGrade grade : REQUIRED_PAID_FORMULAS) {
            if (!durationsByFormula.get(grade).equals(REQUIRED_PAID_DURATIONS)) {
                throw new BusinessException("Subscription plans must include 1, 3 and 12-month variants for " + grade.name());
            }
        }
    }

    private void validateCountryCurrency(String countryCode, List<CatalogPlanInput> plans) {
        if (plans.isEmpty()) {
            return;
        }
        String currency = plans.get(0).currency();
        countryRepository.findByCode(countryCode).ifPresent(country -> {
            String expectedCurrency = normalizeCurrency(country.getCurrency());
            if (!expectedCurrency.equals(currency)) {
                throw new BusinessException("Subscription plan currency must match the configured country currency");
            }
        });
    }

    private BigDecimal resolveTotalPrice(SubscriptionGrade formulaType,
                                         int durationMonths,
                                         BigDecimal monthlyPrice,
                                         BigDecimal totalPrice) {
        BigDecimal normalizedTotalPrice = normalizeAmount(totalPrice);
        if (normalizedTotalPrice == null) {
            if (monthlyPrice == null) {
                throw new BusinessException("Either totalPrice or monthlyPrice must be provided");
            }
            normalizedTotalPrice = normalizeAmount(monthlyPrice.multiply(BigDecimal.valueOf(durationMonths)));
        }

        if (formulaType == SubscriptionGrade.FREE) {
            return normalizedTotalPrice;
        }
        return normalizedTotalPrice;
    }

    private BigDecimal resolvePlanTotalPrice(SubscriptionGrade formulaType,
                                             int durationMonths,
                                             ObjectNode plan,
                                             BigDecimal monthlyPrice) {
        BigDecimal totalPrice = optionalDecimalField(plan, "totalPrice");
        if (totalPrice == null) {
            JsonNode pricingNode = plan.get("pricing");
            if (pricingNode != null && pricingNode.isObject()) {
                JsonNode amountNode = pricingNode.get("amount");
                if (amountNode != null && amountNode.isNumber()) {
                    totalPrice = amountNode.decimalValue();
                }
            }
        }
        return resolveTotalPrice(formulaType, durationMonths, monthlyPrice, totalPrice);
    }

    private SubscriptionGrade resolveFormulaType(ObjectNode plan) {
        String rawValue = firstNonBlank(
                optionalTextField(plan, "formulaType"),
                optionalTextField(plan, "tier"),
                optionalTextField(plan, "code"),
                optionalTextField(plan, "name")
        );
        if (rawValue == null) {
            throw new BusinessException("membership.plans.formulaType is required");
        }

        try {
            return SubscriptionGrade.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported membership formulaType: " + rawValue);
        }
    }

    private int resolveDurationMonths(ObjectNode plan) {
        JsonNode durationNode = plan.get("durationMonths");
        if (durationNode == null || !durationNode.isInt()) {
            throw new BusinessException("membership.plans.durationMonths is required");
        }
        int durationMonths = durationNode.intValue();
        if (durationMonths <= 0) {
            throw new BusinessException("durationMonths must be positive");
        }
        return durationMonths;
    }

    private String resolvePlanCurrency(ObjectNode plan) {
        String currency = optionalTextField(plan, "currency");
        if (currency == null) {
            JsonNode pricingNode = plan.get("pricing");
            if (pricingNode != null && pricingNode.isObject()) {
                JsonNode currencyNode = pricingNode.get("currency");
                if (currencyNode != null && currencyNode.isTextual()) {
                    currency = currencyNode.asText();
                }
            }
        }
        return normalizeCurrency(currency);
    }

    private boolean resolvePlanActive(ObjectNode plan) {
        JsonNode isActiveNode = plan.get("isActive");
        if (isActiveNode != null && isActiveNode.isBoolean()) {
            return isActiveNode.booleanValue();
        }
        JsonNode activeNode = plan.get("active");
        if (activeNode != null && activeNode.isBoolean()) {
            return activeNode.booleanValue();
        }
        return true;
    }

    private BigDecimal optionalDecimalField(ObjectNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new BusinessException(fieldName + " must be numeric");
        }
        return node.decimalValue();
    }

    private String optionalTextField(ObjectNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void ensureCountryExists(String countryCode, String preferredCurrency) {
        countryRepository.findByCode(countryCode).orElseGet(() -> countryRepository.save(Country.builder()
                .id(UUID.nameUUIDFromBytes(("country:" + countryCode).getBytes(StandardCharsets.UTF_8)))
                .code(countryCode)
                .name(countryCode)
                .currency(preferredCurrency)
                .active(true)
                .configured(false)
                .fallback(false)
                .build()));
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("countryCode is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("currency is required");
        }
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        if (normalizedCurrency.length() != 3) {
            throw new BusinessException("currency must be a valid 3-letter code");
        }
        return normalizedCurrency;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.setScale(4, RoundingMode.HALF_UP);
    }

    private String keyOf(SubscriptionGrade formulaType, Integer durationMonths, String currency) {
        return formulaType.name() + "|" + durationMonths + "|" + normalizeCurrency(currency);
    }

    private String resolveCode(CatalogPlanInput input) {
        if (input.formulaType() == SubscriptionGrade.FREE) {
            return "FREE";
        }
        return input.formulaType().name() + "_" + input.durationMonths() + "M";
    }

    private String resolveName(CatalogPlanInput input) {
        if (input.formulaType() == SubscriptionGrade.FREE) {
            return "Free";
        }
        String label = input.formulaType().name().charAt(0) + input.formulaType().name().substring(1).toLowerCase(Locale.ROOT);
        return label + " " + input.durationMonths() + " month" + (input.durationMonths() == 1 ? "" : "s");
    }

    private record CatalogPlanInput(
            SubscriptionGrade formulaType,
            Integer durationMonths,
            BigDecimal monthlyPrice,
            BigDecimal totalPrice,
            String currency,
            boolean isActive
    ) {
    }
}
