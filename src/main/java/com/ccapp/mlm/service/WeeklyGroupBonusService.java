package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.event.WeeklyBonusCalculatedEvent;
import com.ccapp.mlm.domain.entity.MlmLegCarryover;
import com.ccapp.mlm.domain.enums.MlmMemberGrade;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.mlm.repository.MlmLegCarryoverRepository;
import com.ccapp.mlm.repository.MlmMemberGradeRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.projection.MlmWeeklyLegVolumeProjection;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.commission.service.CommissionEventRateLimiter;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyGroupBonusService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String GROUP_BONUS_SOURCE = "WEEKLY_GROUP_BONUS";

    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    private final MlmLegCarryoverRepository mlmLegCarryoverRepository;
    private final MlmMemberGradeRepository mlmMemberGradeRepository;
    private final MlmCommissionRepository mlmCommissionRepository;
    private final FinancialAuditService financialAuditService;
    private final CommissionEventRateLimiter commissionEventRateLimiter;
    private final AccountHolderRepository accountHolderRepository;
    private final CountryRepository countryRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final GlobalMlmConfigService globalMlmConfigService;

    @Value("${mlm.group-bonus.currency:XOF}")
    private String groupBonusCurrency;

    @Value("${mlm.group-bonus.zone:Africa/Porto-Novo}")
    private String groupBonusZone;

    @Value("${mlm.monitoring.commission-spike.threshold:10000}")
    private BigDecimal commissionSpikeThreshold;

    @Value("${commission.generation.max-per-minute:2000}")
    private int commissionGenerationMaxPerMinute;

    @Transactional
    public WeeklyGroupBonusProcessingResult processCurrentWeek() {
        ZoneId zoneId = ZoneId.of(groupBonusZone);
        LocalDate nowDate = OffsetDateTime.now(zoneId).toLocalDate();
        return processWeek(toWeekId(nowDate));
    }

    @Transactional
    public WeeklyGroupBonusProcessingResult processWeek(int weekId) {
        int previousWeekId = previousWeekId(weekId);
        log.info("weekly_group_bonus_started weekId={} previousWeekId={}", weekId, previousWeekId);

        Map<UUID, Map<UUID, BigDecimal>> memberLegVolumes = new HashMap<>();
        for (MlmWeeklyLegVolumeProjection projection : mlmVolumeLedgerRepository.aggregateWeeklyVolumeByMemberAndLeg(weekId)) {
            mergeVolume(memberLegVolumes, projection.getAccountHolderId(), projection.getPlacementChildId(), projection.getTotalVolumeCv());
        }
        for (MlmLegCarryover carryover : mlmLegCarryoverRepository.findByWeekId(previousWeekId)) {
            mergeVolume(memberLegVolumes, carryover.getMemberId(), carryover.getPlacementChildId(), carryover.getVolume());
        }

        mlmLegCarryoverRepository.deleteByWeekId(weekId);
        Set<UUID> membersWithCommissionAlreadyCreated = mlmCommissionRepository
                .findBeneficiaryIdsByCommissionTypeAndWeekId(CommissionType.GROUP_BONUS, weekId);

        if (memberLegVolumes.isEmpty()) {
            return new WeeklyGroupBonusProcessingResult(weekId, 0, 0, 0);
        }

        GlobalMlmConfigService.GlobalBinaryConfig binaryConfig = globalMlmConfigService.resolveActiveConfig().binaryConfig();
        List<MlmLegCarryover> carryoversToSave = new ArrayList<>();
        int membersProcessed = 0;
        int commissionsCreated = 0;

        List<UUID> memberIds = memberLegVolumes.keySet().stream().sorted().toList();
        for (UUID memberId : memberIds) {
            Map<UUID, BigDecimal> legVolumes = memberLegVolumes.get(memberId);
            if (legVolumes == null || legVolumes.isEmpty()) {
                continue;
            }

            MlmMemberGrade memberGrade = resolveMemberGrade(memberId);
            BonusComputation computation = computeBinaryBonus(legVolumes, memberGrade, memberGrade.binaryRate(), binaryConfig);

            if (computation.bonusAmount().signum() > 0
                    && !membersWithCommissionAlreadyCreated.contains(memberId)
                    && createGroupBonusCommissionIfAbsent(memberId, weekId, computation.bonusAmount())) {
                commissionsCreated++;
                membersWithCommissionAlreadyCreated.add(memberId);
            }

            for (Map.Entry<UUID, BigDecimal> remainingByLeg : computation.remainingVolumesByLeg().entrySet()) {
                if (remainingByLeg.getValue().signum() <= 0) {
                    continue;
                }
                carryoversToSave.add(MlmLegCarryover.builder()
                        .memberId(memberId)
                        .placementChildId(remainingByLeg.getKey())
                        .weekId(weekId)
                        .volume(scale(remainingByLeg.getValue()))
                        .build());
            }

            membersProcessed++;
        }

        if (!carryoversToSave.isEmpty()) {
            mlmLegCarryoverRepository.saveAll(carryoversToSave);
        }

        WeeklyGroupBonusProcessingResult result = new WeeklyGroupBonusProcessingResult(
                weekId, membersProcessed, commissionsCreated, carryoversToSave.size());
        domainEventPublisher.publish(WeeklyBonusCalculatedEvent.create(
                result.weekId(),
                result.membersProcessed(),
                result.commissionsCreated(),
                result.carryoversStored()
        ));
        log.info("weekly_group_bonus_completed weekId={} membersProcessed={} commissionsCreated={} carryoversStored={}",
                result.weekId(), result.membersProcessed(), result.commissionsCreated(), result.carryoversStored());
        return result;
    }

    private MlmMemberGrade resolveMemberGrade(UUID memberId) {
        return mlmMemberGradeRepository.findById(memberId)
                .map(profile -> profile.getGrade())
                .orElse(MlmMemberGrade.CLASSIC);
    }

    private boolean createGroupBonusCommissionIfAbsent(UUID memberId, int weekId, BigDecimal amount) {
        if (!commissionEventRateLimiter.allow("group_bonus:" + weekId, commissionGenerationMaxPerMinute)) {
            throw new IllegalStateException("Commission generation rate limit exceeded for weekly group bonus");
        }
        try {
            MlmCommission saved = mlmCommissionRepository.save(MlmCommission.builder()
                    .operationId(null)
                    .beneficiaryAccountHolderId(memberId)
                    .amount(scale(amount))
                    .currency(resolveCurrencyForMember(memberId))
                    .source(GROUP_BONUS_SOURCE)
                    .commissionType(CommissionType.GROUP_BONUS)
                    .generation(0)
                    .weekId(weekId)
                    .build());
            PlatformMonitoringHooks.onCommissionCalculated(saved.getCommissionType().name(), saved.getAmount());
            financialAuditService.record(
                    "COMMISSION_CREATED",
                    saved.getId().toString(),
                    saved.getBeneficiaryAccountHolderId(),
                    saved.getAmount(),
                    Map.of(
                            "commissionType", saved.getCommissionType().name(),
                            "source", saved.getSource(),
                            "weekId", String.valueOf(saved.getWeekId())
                    )
            );
            if (saved.getAmount().compareTo(commissionSpikeThreshold) >= 0) {
                log.warn("commission_spike_detected source={} beneficiaryId={} weekId={} amount={} threshold={}",
                        GROUP_BONUS_SOURCE,
                        saved.getBeneficiaryAccountHolderId(),
                        saved.getWeekId(),
                        saved.getAmount(),
                        commissionSpikeThreshold);
            }
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Weekly group bonus commission already exists for memberId={} weekId={}", memberId, weekId);
            return false;
        }
    }

    private BonusComputation computeBinaryBonus(Map<UUID, BigDecimal> rawLegVolumes,
                                                MlmMemberGrade memberGrade,
                                                BigDecimal planBinaryRate,
                                                GlobalMlmConfigService.GlobalBinaryConfig binaryConfig) {
        Map<UUID, BigDecimal> legVolumes = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> entry : rawLegVolumes.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            BigDecimal volume = scale(entry.getValue());
            if (volume.signum() <= 0) {
                continue;
            }
            legVolumes.put(entry.getKey(), volume);
        }

        if (legVolumes.size() < 2) {
            return new BonusComputation(ZERO, legVolumes);
        }

        if (!GlobalMlmConfigService.DEFAULT_BINARY_MATCHING_RULE.equals(binaryConfig.matchingRule())) {
            throw new IllegalStateException("Unsupported global binary matching rule: " + binaryConfig.matchingRule());
        }
        if (planBinaryRate == null || planBinaryRate.signum() <= 0) {
            return new BonusComputation(ZERO, legVolumes);
        }

        Map.Entry<UUID, BigDecimal> weakestLeg = legVolumes.entrySet().stream()
                .min(Comparator.comparing((Map.Entry<UUID, BigDecimal> e) -> e.getValue())
                        .thenComparing(Map.Entry::getKey))
                .orElseThrow();

        BigDecimal weakestLegVolume = weakestLeg.getValue();
        BigDecimal totalVolume = legVolumes.values().stream()
                .reduce(ZERO, BigDecimal::add);
        BigDecimal strongerLegsVolume = totalVolume.subtract(weakestLegVolume);
        BigDecimal matchedVolume = weakestLegVolume.min(strongerLegsVolume);
        if (matchedVolume.signum() <= 0) {
            return new BonusComputation(ZERO, legVolumes);
        }

        BigDecimal maxMatchedByCap = memberGrade.weeklyCap()
                .multiply(ONE_HUNDRED)
                .divide(planBinaryRate, 4, RoundingMode.DOWN);
        BigDecimal payableMatchedVolume = matchedVolume.min(maxMatchedByCap);
        if (payableMatchedVolume.signum() <= 0) {
            return new BonusComputation(ZERO, legVolumes);
        }

        BigDecimal bonusAmount = scale(
                payableMatchedVolume.multiply(planBinaryRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP)
        );
        if (bonusAmount.compareTo(memberGrade.weeklyCap()) > 0) {
            bonusAmount = memberGrade.weeklyCap();
        }

        Map<UUID, BigDecimal> remainingByLeg = new HashMap<>(legVolumes);
        remainingByLeg.put(weakestLeg.getKey(), scale(weakestLegVolume.subtract(payableMatchedVolume)));

        BigDecimal strongToConsume = payableMatchedVolume;
        List<UUID> strongLegPositions = remainingByLeg.keySet().stream()
                .filter(placementChildId -> !placementChildId.equals(weakestLeg.getKey()))
                .sorted()
                .toList();
        for (UUID placementChildId : strongLegPositions) {
            if (strongToConsume.signum() <= 0) {
                break;
            }
            BigDecimal legVolume = remainingByLeg.get(placementChildId);
            BigDecimal consumed = legVolume.min(strongToConsume);
            remainingByLeg.put(placementChildId, scale(legVolume.subtract(consumed)));
            strongToConsume = scale(strongToConsume.subtract(consumed));
        }

        return new BonusComputation(bonusAmount, remainingByLeg);
    }

    private void mergeVolume(Map<UUID, Map<UUID, BigDecimal>> memberLegVolumes,
                             UUID memberId,
                             UUID placementChildId,
                             BigDecimal volume) {
        if (memberId == null || placementChildId == null || volume == null || volume.signum() <= 0) {
            return;
        }
        Map<UUID, BigDecimal> legMap = memberLegVolumes.computeIfAbsent(memberId, ignored -> new HashMap<>());
        legMap.merge(placementChildId, scale(volume), BigDecimal::add);
    }

    private int previousWeekId(int weekId) {
        int weekYear = weekId / 100;
        int week = weekId % 100;
        LocalDate weekMonday = LocalDate.of(weekYear, 1, 4)
                .with(IsoFields.WEEK_BASED_YEAR, weekYear)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(ChronoField.DAY_OF_WEEK, 1);
        return toWeekId(weekMonday.minusWeeks(1));
    }

    private int toWeekId(LocalDate date) {
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (weekYear * 100) + week;
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String resolveCurrencyForMember(UUID memberId) {
        String countryCode = accountHolderRepository.findById(memberId)
                .map(accountHolder -> {
                    if (accountHolder.getServiceCountryCode() != null && !accountHolder.getServiceCountryCode().isBlank()) {
                        return accountHolder.getServiceCountryCode().trim().toUpperCase();
                    }
                    return accountHolder.getCountryCode();
                })
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .orElse(null);
        if (countryCode == null) {
            return groupBonusCurrency;
        }
        return countryRepository.findByCode(countryCode)
                .map(Country::getCurrency)
                .filter(currency -> currency != null && !currency.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .orElse(groupBonusCurrency);
    }

    private record BonusComputation(
            BigDecimal bonusAmount,
            Map<UUID, BigDecimal> remainingVolumesByLeg
    ) {
    }
}
