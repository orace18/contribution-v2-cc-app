package com.ccapp.payments.subscription.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.payments.commission.service.MlmCommissionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.dto.InitiateSubscriptionRequest;
import com.ccapp.payments.subscription.dto.InitiateSubscriptionResponse;
import com.ccapp.payments.subscription.dto.SubscriptionPlanResponse;
import com.ccapp.payments.subscription.mapper.SubscriptionPlanMapper;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.wallet.service.WalletDebitFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String FALLBACK_COUNTRY_CODE = "INT";
    private static final Set<Integer> REQUIRED_PAID_DURATIONS = Set.of(1, 3, 12);

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final CountryContextService countryContextService;
    private final CountryRepository countryRepository;
    private final AccessControlService accessControlService;
    private final OperationService operationService;
    private final MembershipService membershipService;
    private final WalletDebitFacade walletDebitFacade;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final MlmCommissionService mlmCommissionService;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlans(UUID accountHolderId) {
        String serviceCountryCode = countryContextService.resolve(accountHolderId).serviceCountryCode();
        return getPlansForCountryCode(serviceCountryCode);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlansForCountryCode(String countryCode) {
        return subscriptionPlanRepository
                .findByCountryAndActiveTrueOrderByGradeAscDurationMonthsAscPriceAsc(resolveCatalogCountryCode(countryCode))
                .stream()
                .map(subscriptionPlanMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertCountryPlanCatalogValid(String countryCode) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        List<SubscriptionPlan> activePlans = subscriptionPlanRepository
                .findByCountryAndActiveTrueOrderByGradeAscDurationMonthsAscPriceAsc(normalizedCountryCode);
        if (activePlans.size() != 10) {
            throw new BusinessException("Subscription plans must include exactly 10 active plans: 9 paid variants and 1 FREE plan");
        }

        Map<SubscriptionGrade, Set<Integer>> durationsByGrade = new EnumMap<>(SubscriptionGrade.class);
        for (SubscriptionGrade grade : SubscriptionGrade.values()) {
            durationsByGrade.put(grade, new java.util.HashSet<>());
        }
        int freePlanCount = 0;
        for (SubscriptionPlan plan : activePlans) {
            if (plan.getGrade() == null || plan.getDurationMonths() == null) {
                throw new BusinessException("All active subscription plans must define grade and durationMonths");
            }
            if (plan.getGrade() == SubscriptionGrade.FREE) {
                freePlanCount += 1;
                if (plan.getDurationMonths() != 1) {
                    throw new BusinessException("FREE plan must use durationMonths = 1");
                }
                if (plan.getPrice() == null || plan.getPrice().compareTo(java.math.BigDecimal.ZERO) != 0) {
                    throw new BusinessException("FREE plan must have totalPrice = 0");
                }
            }
            durationsByGrade.get(plan.getGrade()).add(plan.getDurationMonths());
        }

        for (SubscriptionGrade grade : List.of(SubscriptionGrade.CLASSIC, SubscriptionGrade.GOLD, SubscriptionGrade.DIAMOND)) {
            if (!durationsByGrade.get(grade).equals(REQUIRED_PAID_DURATIONS)) {
                throw new BusinessException("Subscription plans must include 1, 3 and 12-month durations for " + grade.name());
            }
        }
        if (freePlanCount != 1) {
            throw new BusinessException("Subscription plans must include exactly one FREE plan");
        }
    }

    @Transactional(readOnly = true)
    public String resolveCatalogCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return FALLBACK_COUNTRY_CODE;
        }
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        return countryRepository.findByCode(normalizedCountryCode)
                .filter(Country::isConfigured)
                .map(Country::getCode)
                .orElse(FALLBACK_COUNTRY_CODE);
    }

    @Transactional
    public InitiateSubscriptionResponse initiate(UUID accountHolderId,
                                                 UUID actorUserId,
                                                 InitiateSubscriptionRequest request) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);

        String serviceCountryCode = countryContextService.resolve(accountHolderId).serviceCountryCode();
        String expectedCatalogCountryCode = resolveCatalogCountryCode(serviceCountryCode);
        SubscriptionPlan plan = subscriptionPlanRepository.findByIdAndActiveTrue(request.planId())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));
        if (!plan.getCountry().equalsIgnoreCase(expectedCatalogCountryCode)) {
            throw new BusinessException("Selected plan is not available for the requested country catalog");
        }

        String idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? "sub:" + UUID.randomUUID()
                : request.idempotencyKey();
        String destinationAccountCode = countryChartOfAccountsService.findOrCreate(
                expectedCatalogCountryCode,
                CountryAccountGroup.MEMBERSHIP_FEES,
                "750001 Cotisations des membres"
        ).getAccountCode();

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        accountHolderId,
                        accountHolderId,
                        actorUserId,
                        null,
                        OperationType.SUBSCRIPTION,
                        plan.getPrice(),
                        plan.getCurrency(),
                        idempotencyKey,
                        "Subscription " + plan.getCode(),
                        buildOtpFingerprint(plan),
                        destinationAccountCode,
                        null,
                        null,
                        null,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        false,
                        false,
                        "Subscription wallet debit",
                        "Subscription revenue recognition",
                        null,
                        null,
                        WalletDebitFacade.SecureDebitPostingMode.OPERATION_POSTING,
                        expectedCatalogCountryCode
                )
        );

        membershipService.createPendingMembership(result.operationId(), accountHolderId, plan);
        if (result.otpRequired()) {
            return new InitiateSubscriptionResponse(
                    result.operationId(),
                    null,
                    null,
                    "OTP_REQUIRED",
                    true,
                    result.otpChallengeId(),
                    result.otpExpiresAt()
            );
        }

        Operation operation = operationService.getById(result.operationId());
        membershipService.activateForConfirmedOperation(operation);
        mlmCommissionService.calculateForOperation(operation);

        return new InitiateSubscriptionResponse(
                operation.getId(),
                null,
                null,
                operation.getStatus().name(),
                false,
                null,
                null
        );
    }

    @Transactional
    public InitiateSubscriptionResponse initiate(UUID accountHolderId, InitiateSubscriptionRequest request) {
        return initiate(accountHolderId, accountHolderId, request);
    }

    private String normalizeCountryCode(String countryCode) {
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String buildOtpFingerprint(SubscriptionPlan plan) {
        return "subscription|"
                + plan.getId()
                + "|" + plan.getPrice().stripTrailingZeros().toPlainString()
                + "|" + plan.getCurrency().trim().toUpperCase(Locale.ROOT);
    }
}
