package com.ccapp.mobile.onboarding.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.onboarding.domain.entity.MobilePlanSelection;
import com.ccapp.mobile.onboarding.domain.enums.MobilePlanSelectionStatus;
import com.ccapp.mobile.onboarding.dto.MobileOnboardingPlanResponse;
import com.ccapp.mobile.onboarding.dto.MobilePlanSelectionResponse;
import com.ccapp.mobile.onboarding.dto.SelectMobilePlanRequest;
import com.ccapp.mobile.onboarding.repository.MobilePlanSelectionRepository;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.dto.SubscriptionPlanResponse;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileOnboardingService {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CountryContextService countryContextService;
    private final AccessControlService accessControlService;
    private final MobilePlanSelectionRepository mobilePlanSelectionRepository;
    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;

    @Transactional(readOnly = true)
    public List<MobileOnboardingPlanResponse> getPlans(String countryCode) {
        String catalogCountryCode = subscriptionService.resolveCatalogCountryCode(countryCode);
        subscriptionService.assertCountryPlanCatalogValid(catalogCountryCode);
        return subscriptionService.getPlansForCountryCode(catalogCountryCode).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional
    public MobilePlanSelectionResponse selectPlan(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  SelectMobilePlanRequest request) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);

        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        String catalogCountryCode = subscriptionService.resolveCatalogCountryCode(countryContext.serviceCountryCode());
        subscriptionService.assertCountryPlanCatalogValid(catalogCountryCode);

        SubscriptionPlan plan = subscriptionPlanRepository.findByIdAndActiveTrue(request.planId())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));
        if (!catalogCountryCode.equalsIgnoreCase(plan.getCountry())) {
            throw new BusinessException("Selected plan is not available for the requested country catalog");
        }

        MobilePlanSelection selection = mobilePlanSelectionRepository.findByAccountHolderId(accountHolderId)
                .orElseGet(() -> MobilePlanSelection.builder()
                        .accountHolderId(accountHolderId)
                        .build());

        selection.setPlanId(plan.getId());
        selection.setPlanCode(plan.getCode());
        selection.setPlanName(displayNameFor(plan.getGrade()));
        selection.setCountryCode(catalogCountryCode);
        selection.setCurrency(plan.getCurrency());
        selection.setStatus(isFree(plan) ? MobilePlanSelectionStatus.READY_FOR_ACTIVATION : MobilePlanSelectionStatus.PENDING_PAYMENT);

        MobilePlanSelection saved = mobilePlanSelectionRepository.save(selection);
        mobileOnboardingJourneyService.onPlanSelected(accountHolderId, plan);
        return toSelectionResponse(saved);
    }

    private MobileOnboardingPlanResponse toPlanResponse(SubscriptionPlanResponse plan) {
        SubscriptionGrade grade = parseGrade(plan.grade());
        BigDecimal totalPrice = defaultAmount(plan.unitAmount());
        BigDecimal monthlyPrice = plan.durationMonths() == null || plan.durationMonths() <= 0
                ? totalPrice
                : totalPrice.divide(BigDecimal.valueOf(plan.durationMonths()), 4, RoundingMode.HALF_UP);
        boolean free = grade == SubscriptionGrade.FREE || totalPrice.compareTo(BigDecimal.ZERO) == 0;

        return new MobileOnboardingPlanResponse(
                plan.id(),
                plan.code(),
                displayNameFor(grade),
                plan.durationMonths(),
                monthlyPrice,
                normalizeCurrency(plan.currency()),
                totalPrice,
                free,
                featuresFor(grade),
                grade == SubscriptionGrade.GOLD && Integer.valueOf(1).equals(plan.durationMonths())
        );
    }

    private MobilePlanSelectionResponse toSelectionResponse(MobilePlanSelection selection) {
        return new MobilePlanSelectionResponse(
                selection.getId(),
                selection.getPlanId(),
                selection.getPlanCode(),
                selection.getPlanName(),
                selection.getStatus().name(),
                switch (selection.getStatus()) {
                    case READY_FOR_ACTIVATION -> new MobilePlanSelectionResponse.NextStep(
                            "ACTIVATE_FREE_PLAN",
                            "Activer votre formule",
                            "Votre formule gratuite est prete a etre activee.",
                            "Activer",
                            "ACTIVATE_FREE_PLAN"
                    );
                    case PENDING_PAYMENT -> new MobilePlanSelectionResponse.NextStep(
                            "PAY_SUBSCRIPTION",
                            "Payer votre formule",
                            "Finalisez votre abonnement pour continuer l'onboarding.",
                            "Payer",
                            "OPEN_SUBSCRIPTION_PAYMENT"
                    );
                }
        );
    }

    private List<String> featuresFor(SubscriptionGrade grade) {
        return switch (grade) {
            case FREE -> List.of(
                    "Rejoins un cercle",
                    "Participe aux prieres",
                    "Epargne ensemble"
            );
            case CLASSIC -> List.of(
                    "Rejoins un cercle",
                    "Participe aux prieres",
                    "Epargne ensemble",
                    "Acces aux activites du club"
            );
            case GOLD -> List.of(
                    "Tout de la formule classique",
                    "Acces au financement",
                    "Formations gratuites",
                    "Prime de service communautaire"
            );
            case DIAMOND -> List.of(
                    "Tout de la formule Gold",
                    "Avantages premium",
                    "Priorite sur les opportunites",
                    "Accompagnement renforce"
            );
        };
    }

    private SubscriptionGrade parseGrade(String rawGrade) {
        if (rawGrade == null || rawGrade.isBlank()) {
            throw new BusinessException("Subscription plan grade is required");
        }
        return SubscriptionGrade.valueOf(rawGrade.trim().toUpperCase(Locale.ROOT));
    }

    private String displayNameFor(SubscriptionGrade grade) {
        return switch (grade) {
            case FREE -> "Free";
            case CLASSIC -> "Classic";
            case GOLD -> "Gold";
            case DIAMOND -> "Diamond";
        };
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : amount;
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isFree(SubscriptionPlan plan) {
        return plan.getGrade() == SubscriptionGrade.FREE
                || (plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) == 0);
    }
}
