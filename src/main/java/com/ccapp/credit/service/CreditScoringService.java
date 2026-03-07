package com.ccapp.credit.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circlesocial.service.CircleScoreService;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.service.CountryService;
import com.ccapp.credit.config.CreditScoringProperties;
import com.ccapp.credit.domain.entity.CreditEligibility;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.repository.CreditEligibilityRepository;
import com.ccapp.credit.service.scoring.CreditScoreFactor;
import com.ccapp.credit.service.scoring.CreditScoringContext;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.progress.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditScoringService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal BASE_MAX_ELIGIBLE_AMOUNT = new BigDecimal("1000000");

    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final CircleRepository circleRepository;
    private final CountryService countryService;
    private final CircleScoreService circleScoreService;
    private final ProgressService progressService;
    private final CreditEligibilityRepository creditEligibilityRepository;
    private final CreditScoringProperties scoringProperties;
    private final List<CreditScoreFactor> scoreFactors;

    @Transactional
    public CreditEligibilityResponse getEligibility(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        return evaluateEligibility(accountHolderId, null);
    }

    @Transactional
    public CreditEligibilityResponse evaluateEligibility(UUID accountHolderId, UUID circleId) {
        AccountHolder accountHolder = ensureAccountHolderExists(accountHolderId);
        String countryCode = countryService.resolveByCodeOrThrow(accountHolder.getCountryCode()).getCode();

        UUID resolvedCircleId = resolveCircleId(accountHolderId, circleId, countryCode);
        CreditScoringContext context = new CreditScoringContext(
                accountHolderId,
                resolvedCircleId,
                () -> circleScoreService.getScore(resolvedCircleId),
                () -> progressService.getUserProgress(accountHolderId)
        );

        Map<String, BigDecimal> scoresByFactor = evaluateFactors(context);

        BigDecimal rmsScore = scoresByFactor.getOrDefault("rms", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal circleScore = scoresByFactor.getOrDefault("circle", BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal eligibilityScore = computeWeightedScore(scoresByFactor);

        BigDecimal maxEligibleAmount = eligibilityScore.compareTo(new BigDecimal("40.00")) < 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BASE_MAX_ELIGIBLE_AMOUNT.multiply(eligibilityScore)
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);

        CreditEligibility eligibility = creditEligibilityRepository.findById(accountHolderId)
                .orElseGet(() -> CreditEligibility.builder().accountHolderId(accountHolderId).build());

        eligibility.setCircleId(resolvedCircleId);
        eligibility.setRmsScore(rmsScore);
        eligibility.setCircleScore(circleScore);
        eligibility.setEligibilityScore(eligibilityScore);
        eligibility.setMaxEligibleAmount(maxEligibleAmount);

        CreditEligibility saved = creditEligibilityRepository.save(eligibility);
        return toResponse(saved);
    }

    private Map<String, BigDecimal> evaluateFactors(CreditScoringContext context) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        for (CreditScoreFactor factor : scoreFactors) {
            BigDecimal raw = factor.evaluate(context);
            scores.put(factor.key(), normalizeScore(raw));
        }
        return scores;
    }

    private BigDecimal computeWeightedScore(Map<String, BigDecimal> scoresByFactor) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : scoresByFactor.entrySet()) {
            BigDecimal weight = scoringProperties.weightFor(entry.getKey());
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            weightedTotal = weightedTotal.add(entry.getValue().multiply(weight));
            totalWeight = totalWeight.add(weight);
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return weightedTotal.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (score.compareTo(HUNDRED) > 0) {
            return HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private UUID resolveCircleId(UUID accountHolderId, UUID requestedCircleId, String countryCode) {
        if (requestedCircleId != null) {
            Circle requestedCircle = circleRepository.findByIdAndStatus(requestedCircleId, CircleStatus.ACTIVE)
                    .orElse(null);
            if (requestedCircle == null || !countryCode.equalsIgnoreCase(requestedCircle.getCountryCode())) {
                throw new NotFoundException("Circle not found");
            }
            return requestedCircleId;
        }

        List<Circle> activeCircles = circleRepository.findActiveCirclesForUser(accountHolderId);
        activeCircles = activeCircles.stream()
                .filter(circle -> countryCode.equalsIgnoreCase(circle.getCountryCode()))
                .toList();
        if (activeCircles.isEmpty()) {
            return null;
        }
        return activeCircles.get(0).getId();
    }

    private AccountHolder ensureAccountHolderExists(UUID accountHolderId) {
        try {
            return accountHolderService.getById(accountHolderId);
        } catch (NotFoundException ex) {
            throw new NotFoundException("Account holder not found");
        }
    }

    private CreditEligibilityResponse toResponse(CreditEligibility eligibility) {
        return new CreditEligibilityResponse(
                eligibility.getAccountHolderId(),
                eligibility.getCircleId(),
                eligibility.getRmsScore(),
                eligibility.getCircleScore(),
                eligibility.getEligibilityScore(),
                eligibility.getMaxEligibleAmount(),
                eligibility.getEvaluatedAt()
        );
    }
}
