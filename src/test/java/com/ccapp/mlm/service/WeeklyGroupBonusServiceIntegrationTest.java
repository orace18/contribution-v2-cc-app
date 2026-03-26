package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.payments.commission.service.CommissionEventRateLimiter;
import com.ccapp.mlm.domain.entity.MlmLegCarryover;
import com.ccapp.mlm.domain.entity.MlmMemberGradeProfile;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.domain.enums.MlmMemberGrade;
import com.ccapp.mlm.repository.MlmLegCarryoverRepository;
import com.ccapp.mlm.repository.MlmMemberGradeRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.platform.config.service.GlobalMlmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({WeeklyGroupBonusService.class, FinancialAuditService.class, CommissionEventRateLimiter.class})
class WeeklyGroupBonusServiceIntegrationTest {

    @Autowired
    private WeeklyGroupBonusService weeklyGroupBonusService;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private MlmLegCarryoverRepository mlmLegCarryoverRepository;
    @Autowired
    private MlmMemberGradeRepository mlmMemberGradeRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private GlobalMlmConfigService globalMlmConfigService;

    @BeforeEach
    void configureGlobalMlmConfig() {
        when(globalMlmConfigService.resolveActiveConfig()).thenReturn(
                new GlobalMlmConfigService.GlobalMlmConfig(
                        Map.of(),
                        new GlobalMlmConfigService.GlobalBinaryConfig(
                                GlobalMlmConfigService.DEFAULT_BINARY_MATCHING_RULE
                        )
                )
        );
    }

    @Test
    void processWeek_shouldComputeGroupBonusAndCarryover() {
        int weekId = 202610;
        int previousWeekId = previousWeekId(weekId);

        UUID memberId = createAccountHolder("Gold", "Leader");
        mlmMemberGradeRepository.save(MlmMemberGradeProfile.builder()
                .memberId(memberId)
                .grade(MlmMemberGrade.GOLD)
                .build());

        UUID leg1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID leg2 = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID leg3 = UUID.fromString("00000000-0000-0000-0000-000000000103");
        seedWeeklyVolume(memberId, leg1, new BigDecimal("100.0000"), weekId);
        seedWeeklyVolume(memberId, leg2, new BigDecimal("70.0000"), weekId);
        seedWeeklyVolume(memberId, leg3, new BigDecimal("30.0000"), weekId);
        mlmLegCarryoverRepository.save(MlmLegCarryover.builder()
                .memberId(memberId)
                .placementChildId(leg2)
                .weekId(previousWeekId)
                .volume(new BigDecimal("20.0000"))
                .build());

        WeeklyGroupBonusProcessingResult result = weeklyGroupBonusService.processWeek(weekId);

        assertEquals(1, result.membersProcessed());
        assertEquals(1, result.commissionsCreated());

        MlmCommission commission = mlmCommissionRepository
                .findByBeneficiaryAccountHolderIdAndCommissionTypeAndWeekId(memberId, CommissionType.GROUP_BONUS, weekId)
                .orElseThrow();
        assertEquals(0, commission.getAmount().compareTo(new BigDecimal("3.0000")));

        List<MlmLegCarryover> carryovers = mlmLegCarryoverRepository.findByWeekId(weekId).stream()
                .filter(c -> c.getMemberId().equals(memberId))
                .toList();
        assertEquals(2, carryovers.size());
        Map<UUID, BigDecimal> carryoverByPlacement = carryovers.stream()
                .collect(Collectors.toMap(MlmLegCarryover::getPlacementChildId, MlmLegCarryover::getVolume));
        assertEquals(0, carryoverByPlacement.get(leg1).compareTo(new BigDecimal("70.0000")));
        assertEquals(0, carryoverByPlacement.get(leg2).compareTo(new BigDecimal("90.0000")));
    }

    @Test
    void processWeek_shouldApplyCapAndBeIdempotentOnRerun() {
        int weekId = 202611;

        UUID memberId = createAccountHolder("Classic", "Member");
        UUID leg1 = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID leg2 = UUID.fromString("00000000-0000-0000-0000-000000000202");
        seedWeeklyVolume(memberId, leg1, new BigDecimal("500000.0000"), weekId);
        seedWeeklyVolume(memberId, leg2, new BigDecimal("500000.0000"), weekId);

        WeeklyGroupBonusProcessingResult firstRun = weeklyGroupBonusService.processWeek(weekId);
        WeeklyGroupBonusProcessingResult secondRun = weeklyGroupBonusService.processWeek(weekId);

        assertEquals(1, firstRun.commissionsCreated());
        assertEquals(0, secondRun.commissionsCreated());

        assertEquals(1, mlmCommissionRepository.countByCommissionTypeAndWeekId(CommissionType.GROUP_BONUS, weekId));
        MlmCommission commission = mlmCommissionRepository
                .findByBeneficiaryAccountHolderIdAndCommissionTypeAndWeekId(memberId, CommissionType.GROUP_BONUS, weekId)
                .orElseThrow();
        assertEquals(0, commission.getAmount().compareTo(new BigDecimal("25000.0000")));

        List<MlmLegCarryover> carryovers = mlmLegCarryoverRepository.findByWeekId(weekId).stream()
                .filter(c -> c.getMemberId().equals(memberId))
                .toList();
        assertEquals(2, carryovers.size());
        Map<UUID, BigDecimal> carryoverByPlacement = carryovers.stream()
                .collect(Collectors.toMap(MlmLegCarryover::getPlacementChildId, MlmLegCarryover::getVolume));
        assertEquals(0, carryoverByPlacement.get(leg1).compareTo(new BigDecimal("250000.0000")));
        assertEquals(0, carryoverByPlacement.get(leg2).compareTo(new BigDecimal("250000.0000")));

        assertTrue(carryovers.stream().allMatch(c -> c.getVolume().compareTo(BigDecimal.ZERO) > 0));
    }

    private void seedWeeklyVolume(UUID memberId, UUID placementChildId, BigDecimal volume, int weekId) {
        ensureRelationship(memberId, memberId, 0);

        createAccountHolderWithId(placementChildId, "Placement", "Child");
        UUID sourceMemberId = createAccountHolder("Source", "Member");
        ensureRelationship(placementChildId, placementChildId, 0);
        ensureRelationship(sourceMemberId, sourceMemberId, 0);
        ensureRelationship(memberId, placementChildId, 1);
        ensureRelationship(placementChildId, sourceMemberId, 1);
        ensureRelationship(memberId, sourceMemberId, 2);

        mlmVolumeLedgerRepository.save(MlmVolumeLedger.builder()
                .accountHolderId(memberId)
                .sourceMemberId(sourceMemberId)
                .sourceOrderId(UUID.randomUUID())
                .volumeCv(volume.setScale(4, RoundingMode.HALF_UP))
                .weekId(weekId)
                .build());
    }

    private void ensureRelationship(UUID ancestorId, UUID descendantId, int level) {
        if (mlmRelationshipRepository.existsByAncestorIdAndDescendantId(ancestorId, descendantId)) {
            return;
        }
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .createdAt(OffsetDateTime.now().minusDays(2))
                .build());
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        UUID accountHolderId = UUID.randomUUID();
        createAccountHolderWithId(accountHolderId, firstName, lastName);
        return accountHolderId;
    }

    private void createAccountHolderWithId(UUID accountHolderId, String firstName, String lastName) {
        accountHolderRepository.save(AccountHolder.builder()
                .id(accountHolderId)
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build());
    }

    private int previousWeekId(int weekId) {
        int weekYear = weekId / 100;
        int week = weekId % 100;
        LocalDate weekMonday = LocalDate.of(weekYear, 1, 4)
                .with(IsoFields.WEEK_BASED_YEAR, weekYear)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate previousWeek = weekMonday.minusWeeks(1);
        int previousWeekYear = previousWeek.get(IsoFields.WEEK_BASED_YEAR);
        int previousIsoWeek = previousWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (previousWeekYear * 100) + previousIsoWeek;
    }
}
