package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.graph.MlmGraphNeo4jGateway;
import com.ccapp.mlm.graph.MlmGraphService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.domain.entity.MlmWeeklySnapshot;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.MlmWeeklySnapshotRepository;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
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
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({MlmAnalyticsService.class, MlmGraphService.class})
class MlmAnalyticsServiceIntegrationTest {

    @Autowired
    private MlmAnalyticsService mlmAnalyticsService;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private MlmWeeklySnapshotRepository mlmWeeklySnapshotRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @MockBean
    private MlmGraphNeo4jGateway mlmGraphNeo4jGateway;

    @Test
    void refreshAndSnapshot_shouldUseLedgerAndCommissions() {
        when(mlmGraphNeo4jGateway.isAvailable()).thenReturn(false);

        UUID a = createAccountHolder("A", "Leader");
        UUID b = createAccountHolder("B", "Node");
        UUID d = createAccountHolder("D", "Buyer");
        UUID sourceMember = createAccountHolder("Source", "Member");

        seedRelationship(a, a, 0);
        seedRelationship(a, b, 1);
        seedRelationship(a, d, 2);

        int weekId = 202611;
        mlmVolumeLedgerRepository.save(MlmVolumeLedger.builder()
                .accountHolderId(a)
                .sourceMemberId(sourceMember)
                .sourceOrderId(UUID.randomUUID())
                .volumeCv(new BigDecimal("5.2000"))
                .weekId(weekId)
                .createdAt(OffsetDateTime.now().minusDays(2))
                .build());
        mlmCommissionRepository.save(MlmCommission.builder()
                .operationId(UUID.randomUUID())
                .beneficiaryAccountHolderId(a)
                .amount(new BigDecimal("3.6000"))
                .currency("XOF")
                .source("WEEKLY_GROUP_BONUS")
                .commissionType(CommissionType.GROUP_BONUS)
                .weekId(weekId)
                .status(CommissionStatus.PAID)
                .build());

        ReflectionTestUtils.setField(mlmAnalyticsService, "analyticsZone", "UTC");
        ReflectionTestUtils.setField(mlmAnalyticsService, "activeWindowDays", 30);

        var metrics = mlmAnalyticsService.refreshMemberMetrics(a);

        assertEquals(2L, metrics.getTeamSize());
        assertEquals(1L, metrics.getActiveTeamMembers());
        assertEquals(0, metrics.getTotalVolume().compareTo(new BigDecimal("5.2000")));
        assertEquals(0, metrics.getTotalCommissions().compareTo(new BigDecimal("3.6000")));

        int snapshotsCreated = mlmAnalyticsService.captureWeekSnapshot(weekId);
        assertEquals(1, snapshotsCreated);

        MlmWeeklySnapshot snapshot = mlmWeeklySnapshotRepository.findByWeekIdAndAccountHolderId(weekId, a).orElseThrow();
        assertEquals(0, snapshot.getTeamVolume().compareTo(new BigDecimal("5.2000")));
        assertEquals(1L, snapshot.getTeamMembers());
        assertEquals(0, snapshot.getCommissionsGenerated().compareTo(new BigDecimal("3.6000")));
        assertEquals(0, snapshot.getCommissionsPaid().compareTo(new BigDecimal("3.6000")));
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

    private void seedRelationship(UUID ancestorId, UUID descendantId, int level) {
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .createdAt(OffsetDateTime.now().minusDays(5))
                .build());
    }
}
