package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmCommissionService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int MAX_GENERATIONS = 10;
    private static final String COMMISSION_SOURCE = "SUBSCRIPTION_UNILEVEL";

    private final MlmCommissionRepository mlmCommissionRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final FinancialAuditService financialAuditService;
    private final CommissionEventRateLimiter commissionEventRateLimiter;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MembershipRepository membershipRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CountryConfigResolver countryConfigResolver;
    private final GlobalMlmConfigService globalMlmConfigService;

    @Value("${mlm.monitoring.commission-spike.threshold:10000}")
    private BigDecimal commissionSpikeThreshold;

    @Value("${commission.generation.max-per-minute:2000}")
    private int commissionGenerationMaxPerMinute;

    @Transactional
    public Optional<MlmCommission> calculateForOperation(Operation operation) {
        if (operation == null || operation.getType() != OperationType.SUBSCRIPTION) {
            return Optional.empty();
        }

        if (!commissionEventRateLimiter.allow("subscription:" + operation.getId(), commissionGenerationMaxPerMinute)) {
            throw new BusinessException("Commission generation rate limit exceeded");
        }

        UnilevelProfile profile = resolveUnilevelProfile(operation);
        log.info("subscription_commission_profile_resolved operationId={} accountHolderId={} planId={} countryCode={} commissionPoolPercent={} generationRates={}",
                operation.getId(),
                operation.getAccountHolderId(),
                operation.getPlanId(),
                profile.countryCode(),
                profile.commissionPoolPercent(),
                profile.ratesByGeneration());
        List<MlmRelationship> ancestors = mlmRelationshipRepository.findAncestors(operation.getAccountHolderId()).stream()
                .filter(relationship -> relationship.getLevel() != null
                        && relationship.getLevel() > 0
                        && relationship.getLevel() <= MAX_GENERATIONS)
                .toList();

        if (ancestors.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal commissionPoolAmount = operation.getAmount()
                .multiply(profile.commissionPoolPercent())
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        if (commissionPoolAmount.signum() <= 0) {
            return Optional.empty();
        }

        List<MlmCommission> created = new ArrayList<>();
        int weekId = currentWeekId();
        for (MlmRelationship ancestor : ancestors) {
            int generation = ancestor.getLevel();
            BigDecimal generationRate = profile.generationRate(generation);
            if (generationRate == null || generationRate.signum() <= 0) {
                continue;
            }
            if (mlmCommissionRepository.existsByOperationIdAndBeneficiaryAccountHolderIdAndGeneration(
                    operation.getId(),
                    ancestor.getAncestorId(),
                    generation
            )) {
                continue;
            }

            Membership activeMembership = membershipRepository
                    .findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                            ancestor.getAncestorId(),
                            MembershipStatus.ACTIVE,
                            OffsetDateTime.now()
                    )
                    .orElse(null);
            if (activeMembership == null) {
                continue;
            }

            BigDecimal generationAmount = commissionPoolAmount
                    .multiply(generationRate)
                    .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
            if (generationAmount.signum() <= 0) {
                continue;
            }

            BigDecimal earningCap = resolveGradeCap(activeMembership);
            if (earningCap == null || earningCap.signum() <= 0) {
                continue;
            }
            BigDecimal currentWeekAmount = mlmCommissionRepository
                    .sumAmountByBeneficiaryAccountHolderIdAndWeekIdAndCommissionType(
                            ancestor.getAncestorId(),
                            weekId,
                            CommissionType.SUBSCRIPTION
                    );
            BigDecimal remainingCap = earningCap.subtract(currentWeekAmount == null ? BigDecimal.ZERO : currentWeekAmount);
            if (remainingCap.signum() <= 0) {
                continue;
            }

            BigDecimal payableAmount = generationAmount.min(remainingCap).setScale(4, RoundingMode.HALF_UP);
            if (payableAmount.signum() <= 0) {
                continue;
            }

            MlmCommission saved = mlmCommissionRepository.save(MlmCommission.builder()
                    .operationId(operation.getId())
                    .beneficiaryAccountHolderId(ancestor.getAncestorId())
                    .amount(payableAmount)
                    .currency(operation.getCurrency())
                    .source(COMMISSION_SOURCE)
                    .commissionType(CommissionType.SUBSCRIPTION)
                    .generation(generation)
                    .weekId(weekId)
                    .status(CommissionStatus.PENDING)
                    .build());
            created.add(saved);
            PlatformMonitoringHooks.onCommissionCalculated(saved.getCommissionType().name(), saved.getAmount());
            financialAuditService.record(
                    "COMMISSION_CREATED",
                    saved.getId() != null ? saved.getId().toString() : operation.getId().toString(),
                    saved.getBeneficiaryAccountHolderId(),
                    saved.getAmount(),
                    Map.of(
                            "commissionType", saved.getCommissionType().name(),
                            "source", saved.getSource(),
                            "operationId", String.valueOf(saved.getOperationId()),
                            "generation", String.valueOf(generation)
                    )
            );
            if (saved.getAmount().compareTo(commissionSpikeThreshold) >= 0) {
                log.warn("commission_spike_detected operationId={} beneficiaryId={} amount={} threshold={}",
                        saved.getOperationId(),
                        saved.getBeneficiaryAccountHolderId(),
                        saved.getAmount(),
                        commissionSpikeThreshold);
            }
            domainEventPublisher.publish(CommissionGeneratedEvent.create(
                    saved.getOperationId(),
                    saved.getBeneficiaryAccountHolderId(),
                    saved.getAmount(),
                    saved.getCurrency(),
                    saved.getSource()
            ));
        }

        return created.stream().findFirst();
    }

    private UnilevelProfile resolveUnilevelProfile(Operation operation) {
        String countryCode = resolveOperationCountryCode(operation);
        var generalConfig = countryConfigResolver.resolveActiveConfig(countryCode, CountryConfigType.GENERAL);
        var generalPayload = countryConfigResolver.payloadAsObjectNode(generalConfig);
        BigDecimal commissionPoolPercent = readDecimalFromPercentages(generalPayload, "commissionPool", "commission_pool");
        if (commissionPoolPercent == null || commissionPoolPercent.signum() <= 0) {
            log.error("subscription_commission_profile_resolution_failed operationId={} accountHolderId={} planId={} countryCode={} generalConfigSource={} generalConfigVersion={} message={}",
                    operation.getId(),
                    operation.getAccountHolderId(),
                    operation.getPlanId(),
                    countryCode,
                    generalConfig.source(),
                    generalConfig.version(),
                    "commissionPool allocation percentage is required");
            throw new BusinessException("commissionPool allocation percentage is required in GENERAL config for country "
                    + countryCode
                    + " (source="
                    + generalConfig.source()
                    + ", version="
                    + generalConfig.version()
                    + ")");
        }

        var globalRatesConfig = countryConfigResolver.resolveActivePlatformConfig(PlatformConfigType.GLOBAL_MLM_BASE_RATES);
        var globalMlmConfig = globalMlmConfigService.resolveActiveConfig();
        Map<Integer, BigDecimal> ratesByGeneration = new HashMap<>();
        for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
            BigDecimal rate = globalMlmConfig.generationRate(generation);
            if (rate == null) {
                throw new BusinessException("Missing global unilevel rate for generation G" + generation);
            }
            ratesByGeneration.put(generation, rate);
        }
        log.info("subscription_commission_rates_loaded operationId={} accountHolderId={} planId={} countryCode={} globalMlmConfigVersion={} generationCount={}",
                operation.getId(),
                operation.getAccountHolderId(),
                operation.getPlanId(),
                countryCode,
                globalRatesConfig.version(),
                ratesByGeneration.size());
        return new UnilevelProfile(countryCode, commissionPoolPercent, ratesByGeneration);
    }

    private BigDecimal resolveGradeCap(Membership membership) {
        SubscriptionGrade grade = membership.getPlanGrade();
        if (grade == null && membership.getPlanId() != null) {
            grade = subscriptionPlanRepository.findById(membership.getPlanId())
                    .map(SubscriptionPlan::getGrade)
                    .orElse(null);
        }
        if (grade == null) {
            return BigDecimal.ZERO;
        }

        var mlmConfig = countryConfigResolver.resolveActiveConfig(membership.getCountryCode(), CountryConfigType.MLM_LIMITS);
        var payload = countryConfigResolver.payloadAsObjectNode(mlmConfig);
        var weeklyCapsNode = payload.get("weeklyCapsByTier");
        if (weeklyCapsNode != null && weeklyCapsNode.isArray()) {
            for (var iterator = weeklyCapsNode.elements(); iterator.hasNext(); ) {
                var item = iterator.next();
                if (!item.isObject()) {
                    continue;
                }
                var tierNode = item.get("tier");
                var capNode = item.get("cap");
                if (tierNode != null && tierNode.isTextual()
                        && grade.name().equalsIgnoreCase(tierNode.asText())
                        && capNode != null && capNode.isNumber()) {
                    return capNode.decimalValue().setScale(4, RoundingMode.HALF_UP);
                }
            }
        }
        var weeklyCap = payload.get("weeklyCap");
        if (weeklyCap != null && weeklyCap.isNumber()) {
            return weeklyCap.decimalValue().setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private String resolveOperationCountryCode(Operation operation) {
        if (operation.getPlanId() != null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(operation.getPlanId()).orElse(null);
            if (plan != null && plan.getCountry() != null && !plan.getCountry().isBlank()) {
                return plan.getCountry().trim().toUpperCase(Locale.ROOT);
            }
        }
        Membership membership = membershipRepository.findByOperationId(operation.getId()).orElse(null);
        if (membership != null && membership.getCountryCode() != null && !membership.getCountryCode().isBlank()) {
            return membership.getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        throw new BusinessException("Unable to resolve subscription country for commission generation");
    }

    private BigDecimal readDecimalFromPercentages(com.fasterxml.jackson.databind.node.ObjectNode payload, String... aliases) {
        if (payload == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode node = payload.has("percentages") && payload.get("percentages").isObject()
                ? payload.get("percentages")
                : payload;
        for (String alias : aliases) {
            com.fasterxml.jackson.databind.JsonNode value = node.get(alias);
            if (value != null && value.isNumber()) {
                return value.decimalValue();
            }
        }
        return null;
    }

    private int currentWeekId() {
        OffsetDateTime now = OffsetDateTime.now();
        return (now.get(IsoFields.WEEK_BASED_YEAR) * 100) + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private record UnilevelProfile(
            String countryCode,
            BigDecimal commissionPoolPercent,
            Map<Integer, BigDecimal> ratesByGeneration
    ) {
        private BigDecimal generationRate(int generation) {
            return ratesByGeneration.get(generation);
        }
    }
}
