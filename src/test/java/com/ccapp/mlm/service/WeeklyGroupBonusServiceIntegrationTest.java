package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.payments.commission.service.CommissionEventRateLimiter;
import com.ccapp.mlm.domain.entity.MlmLegCarryover;
import com.ccapp.mlm.domain.entity.MlmMemberGradeProfile;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.domain.enums.MlmMemberGrade;
import com.ccapp.mlm.repository.MlmLegCarryoverRepository;
import com.ccapp.mlm.repository.MlmMemberGradeRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private MlmLegCarryoverRepository mlmLegCarryoverRepository;
    @Autowired
    private MlmMemberGradeRepository mlmMemberGradeRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @Test
    void processWeek_shouldComputeGroupBonusAndCarryover() {
        int weekId = 202610;
        int previousWeekId = previousWeekId(weekId);

        UUID memberId = createAccountHolder("Gold", "Leader");
        mlmMemberGradeRepository.save(MlmMemberGradeProfile.builder()
                .memberId(memberId)
                .grade(MlmMemberGrade.GOLD)
                .build());

        mlmLegCarryoverRepository.save(MlmLegCarryover.builder()
                .memberId(memberId)
                .legPosition(2)
                .weekId(previousWeekId)
                .volume(new BigDecimal("20.0000"))
                .build());

        seedWeeklyVolume(memberId, 1, new BigDecimal("100.0000"), weekId);
        seedWeeklyVolume(memberId, 2, new BigDecimal("70.0000"), weekId);
        seedWeeklyVolume(memberId, 3, new BigDecimal("30.0000"), weekId);

        WeeklyGroupBonusProcessingResult result = weeklyGroupBonusService.processWeek(weekId);

        assertEquals(1, result.membersProcessed());
        assertEquals(1, result.commissionsCreated());

        MlmCommission commission = mlmCommissionRepository
                .findByBeneficiaryAccountHolderIdAndCommissionTypeAndWeekId(memberId, CommissionType.GROUP_BONUS, weekId)
                .orElseThrow();
        assertEquals(0, commission.getAmount().compareTo(new BigDecimal("3.6000")));

        List<MlmLegCarryover> carryovers = mlmLegCarryoverRepository.findByWeekId(weekId).stream()
                .filter(c -> c.getMemberId().equals(memberId))
                .sorted(Comparator.comparing(MlmLegCarryover::getLegPosition))
                .toList();
        assertEquals(2, carryovers.size());
        assertEquals(1, carryovers.get(0).getLegPosition());
        assertEquals(0, carryovers.get(0).getVolume().compareTo(new BigDecimal("70.0000")));
        assertEquals(2, carryovers.get(1).getLegPosition());
        assertEquals(0, carryovers.get(1).getVolume().compareTo(new BigDecimal("90.0000")));
    }

    @Test
    void processWeek_shouldApplyCapAndBeIdempotentOnRerun() {
        int weekId = 202611;

        UUID memberId = createAccountHolder("Classic", "Member");
        seedWeeklyVolume(memberId, 1, new BigDecimal("500000.0000"), weekId);
        seedWeeklyVolume(memberId, 2, new BigDecimal("500000.0000"), weekId);

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
                .sorted(Comparator.comparing(MlmLegCarryover::getLegPosition))
                .toList();
        assertEquals(2, carryovers.size());
        assertEquals(0, carryovers.get(0).getVolume().compareTo(new BigDecimal("250000.0000")));
        assertEquals(0, carryovers.get(1).getVolume().compareTo(new BigDecimal("250000.0000")));

        assertTrue(carryovers.stream().allMatch(c -> c.getVolume().compareTo(BigDecimal.ZERO) > 0));
    }

    private void seedWeeklyVolume(UUID memberId, int legPosition, BigDecimal volume, int weekId) {
        UUID sourceMemberId = createAccountHolder("Source", "Member");
        mlmVolumeLedgerRepository.save(MlmVolumeLedger.builder()
                .accountHolderId(memberId)
                .sourceMemberId(sourceMemberId)
                .sourceOrderId(UUID.randomUUID())
                .legPosition(legPosition)
                .volumeCv(volume.setScale(4, RoundingMode.HALF_UP))
                .weekId(weekId)
                .build());
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        AccountHolder saved = accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build());
        return saved.getId();
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
