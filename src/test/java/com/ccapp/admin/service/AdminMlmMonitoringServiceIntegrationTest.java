package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminMlmNetworkHealthResponse;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmMemberMetrics;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.domain.entity.MlmWeeklyBonusJobRun;
import com.ccapp.mlm.domain.entity.MlmWeeklySnapshot;
import com.ccapp.mlm.domain.enums.WeeklyBonusJobRunStatus;
import com.ccapp.mlm.repository.MlmMemberMetricsRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.MlmWeeklyBonusJobRunRepository;
import com.ccapp.mlm.repository.MlmWeeklySnapshotRepository;
import com.ccapp.mlm.scheduler.MlmLedgerRebuildJob;
import com.ccapp.mlm.service.MlmAnalyticsService;
import com.ccapp.payments.commission.domain.entity.CommissionPayoutBatch;
import com.ccapp.payments.commission.domain.entity.CommissionPayoutItem;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionPayoutBatchStatus;
import com.ccapp.payments.commission.domain.enums.CommissionPayoutItemStatus;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionPayoutBatchRepository;
import com.ccapp.payments.commission.repository.CommissionPayoutItemRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AdminMlmMonitoringService.class, MlmAnalyticsService.class})
class AdminMlmMonitoringServiceIntegrationTest {

    @Autowired
    private AdminMlmMonitoringService adminMlmMonitoringService;
    @Autowired
    private MlmAnalyticsService mlmAnalyticsService;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private MlmMemberMetricsRepository mlmMemberMetricsRepository;
    @Autowired
    private MlmWeeklySnapshotRepository mlmWeeklySnapshotRepository;
    @Autowired
    private MlmWeeklyBonusJobRunRepository mlmWeeklyBonusJobRunRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private CommissionPayoutBatchRepository commissionPayoutBatchRepository;
    @Autowired
    private CommissionPayoutItemRepository commissionPayoutItemRepository;
    @MockBean
    private MlmLedgerRebuildJob mlmLedgerRebuildJob;

    @Test
    void monitoringApis_shouldReturnHealthCommissionsPayoutsAndWeeklyVolume() {
        ReflectionTestUtils.setField(mlmAnalyticsService, "analyticsZone", "UTC");
        int weekId = mlmAnalyticsService.currentWeekId();

        UUID memberId = createAccountHolder("Admin", "MLM");
        UUID sourceMemberId = createAccountHolder("Source", "User");

        mlmMemberMetricsRepository.save(MlmMemberMetrics.builder()
                .accountHolderId(memberId)
                .teamSize(15L)
                .activeTeamMembers(3L)
                .totalVolume(new BigDecimal("123.4500"))
                .totalCommissions(new BigDecimal("22.0000"))
                .build());
        mlmWeeklySnapshotRepository.save(MlmWeeklySnapshot.builder()
                .weekId(weekId)
                .accountHolderId(memberId)
                .teamVolume(new BigDecimal("30.0000"))
                .teamMembers(2L)
                .commissionsGenerated(new BigDecimal("10.0000"))
                .commissionsPaid(new BigDecimal("0.0000"))
                .build());
        mlmWeeklyBonusJobRunRepository.save(MlmWeeklyBonusJobRun.builder()
                .weekId(weekId)
                .status(WeeklyBonusJobRunStatus.SUCCESS)
                .membersProcessed(10)
                .commissionsCreated(5)
                .carryoversStored(2)
                .startedAt(OffsetDateTime.now().minusMinutes(2))
                .finishedAt(OffsetDateTime.now().minusMinutes(1))
                .build());

        shopOrderRepository.save(ShopOrder.builder()
                .buyerId(memberId)
                .status(ShopOrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("100.0000"))
                .currency("XOF")
                .paymentMethod("CARD")
                .deliveryAddressId(UUID.randomUUID())
                .build());

        mlmVolumeLedgerRepository.save(MlmVolumeLedger.builder()
                .accountHolderId(memberId)
                .sourceMemberId(sourceMemberId)
                .sourceOrderId(UUID.randomUUID())
                .legPosition(1)
                .volumeCv(new BigDecimal("5.0000"))
                .weekId(weekId)
                .build());

        mlmCommissionRepository.save(MlmCommission.builder()
                .operationId(UUID.randomUUID())
                .beneficiaryAccountHolderId(memberId)
                .amount(new BigDecimal("20000.0000"))
                .currency("XOF")
                .source("WEEKLY_GROUP_BONUS")
                .commissionType(CommissionType.GROUP_BONUS)
                .weekId(weekId)
                .status(CommissionStatus.PENDING)
                .build());

        CommissionPayoutBatch batch = commissionPayoutBatchRepository.save(CommissionPayoutBatch.builder()
                .weekId(weekId - 1)
                .status(CommissionPayoutBatchStatus.COMPLETED)
                .build());
        commissionPayoutItemRepository.save(CommissionPayoutItem.builder()
                .batchId(batch.getId())
                .accountHolderId(memberId)
                .grossAmount(new BigDecimal("100.0000"))
                .taxAmount(new BigDecimal("10.0000"))
                .netAmount(new BigDecimal("90.0000"))
                .status(CommissionPayoutItemStatus.PAID)
                .processedAt(OffsetDateTime.now().minusHours(1))
                .build());

        ReflectionTestUtils.setField(adminMlmMonitoringService, "spikeThreshold", new BigDecimal("10000"));
        ReflectionTestUtils.setField(adminMlmMonitoringService, "spikeLookbackHours", 48);

        AdminMlmNetworkHealthResponse health = adminMlmMonitoringService.getNetworkHealth();
        assertEquals(weekId, health.currentWeekId());
        assertEquals(1, health.membersWithMetrics());
        assertEquals(1, health.weeklySnapshotsCount());
        assertTrue(health.weeklyBonusExecutionHealthy());
        assertEquals(1, health.deliveredOrdersWithoutVolumeLedger());
        assertFalse(health.volumeLedgerUsageHealthy());
        assertEquals(1, health.abnormalCommissionSpikes());

        var commissions = adminMlmMonitoringService.listCommissions(weekId, CommissionType.GROUP_BONUS, CommissionStatus.PENDING, 0, 20);
        assertEquals(1, commissions.size());
        assertEquals(memberId, commissions.getFirst().beneficiaryAccountHolderId());

        var payouts = adminMlmMonitoringService.listCommissionPayouts(0, 20);
        assertEquals(1, payouts.size());
        assertEquals(1, payouts.getFirst().items().size());

        var weeklyVolumes = adminMlmMonitoringService.listWeeklyVolume(1);
        assertEquals(1, weeklyVolumes.size());
        assertEquals(weekId, weeklyVolumes.getFirst().weekId());
        assertEquals(0, weeklyVolumes.getFirst().totalVolume().compareTo(new BigDecimal("5.0000")));
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
}
