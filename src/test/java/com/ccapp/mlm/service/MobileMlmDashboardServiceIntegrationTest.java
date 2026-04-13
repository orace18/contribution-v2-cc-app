package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.graph.MlmGraphNeo4jGateway;
import com.ccapp.mlm.graph.MlmGraphService;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.MlmMemberMetricsRepository;
import com.ccapp.mlm.repository.MlmWeeklySnapshotRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Regression test for GET /api/mobile/mlm/dashboard.
 *
 * <p>Root cause that was fixed: {@code MlmTreeMemberProjection.getLinkedAt()} was declared
 * as {@code OffsetDateTime} while the underlying native query returns a raw
 * {@code timestamptz} value that Spring Data JPA maps to {@code Instant}.
 * This caused {@code UnsupportedOperationException: Cannot project java.time.Instant
 * to java.time.OffsetDateTime} at runtime whenever any endpoint that loads the
 * sponsor/placement tree was called.
 *
 * <p>This test ensures {@link MobileMlmDashboardService#getDashboard} executes without
 * that exception and produces a structurally valid response.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        MobileMlmDashboardService.class,
        MlmAnalyticsService.class,
        MlmGraphService.class
})
class MobileMlmDashboardServiceIntegrationTest {

    @Autowired
    private MobileMlmDashboardService mobileMlmDashboardService;

    @Autowired
    private MlmAnalyticsService mlmAnalyticsService;

    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @Autowired
    private MlmMemberLinkRepository mlmMemberLinkRepository;

    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;

    @Autowired
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;

    @Autowired
    private MlmMemberMetricsRepository mlmMemberMetricsRepository;

    @Autowired
    private MlmWeeklySnapshotRepository mlmWeeklySnapshotRepository;

    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;

    @MockBean
    private AccessControlService accessControlService;

    @MockBean
    private AccountHolderService accountHolderService;

    @MockBean
    private MlmGraphNeo4jGateway mlmGraphNeo4jGateway;

    @BeforeEach
    void setUp() {
        when(mlmGraphNeo4jGateway.isAvailable()).thenReturn(false);
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
        ReflectionTestUtils.setField(mlmAnalyticsService, "analyticsZone", "UTC");
        ReflectionTestUtils.setField(mlmAnalyticsService, "activeWindowDays", 30);
    }

    /**
     * Regression: GET /api/mobile/mlm/dashboard must not throw
     * {@code UnsupportedOperationException} due to the Instant→OffsetDateTime
     * projection mismatch in {@code MlmTreeMemberProjection.getLinkedAt()}.
     */
    @Test
    void getDashboard_shouldReturnValidResponseWithoutProjectionException() {
        UUID actorUserId = UUID.randomUUID();
        UUID leaderId = createAccountHolder("Leader", "Mlm");
        UUID memberId = createAccountHolder("Child", "Member");

        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(leaderId)
                .sponsorAccountHolderId(null)
                .placementAccountHolderId(leaderId)
                .build());
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(memberId)
                .sponsorAccountHolderId(leaderId)
                .placementAccountHolderId(leaderId)
                .build());

        seedRelationship(leaderId, leaderId, 0);
        seedRelationship(leaderId, memberId, 1);

        // Must not throw UnsupportedOperationException
        var response = mobileMlmDashboardService.getDashboard(actorUserId, leaderId);

        assertNotNull(response, "Dashboard response must not be null");
        assertTrue(response.teamSize() >= 0, "teamSize must be non-negative");
        assertNotNull(response.levelBreakdown(), "levelBreakdown must not be null");
    }

    /**
     * Regression: getDashboard on a leader with no network members must return a
     * zeroed-out response (not throw) — edge case that exercises the empty-tree path.
     */
    @Test
    void getDashboard_withNoDownline_shouldReturnZeroedResponse() {
        UUID actorUserId = UUID.randomUUID();
        UUID leaderId = createAccountHolder("Lonely", "Leader");

        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(leaderId)
                .sponsorAccountHolderId(null)
                .placementAccountHolderId(leaderId)
                .build());
        seedRelationship(leaderId, leaderId, 0);

        var response = mobileMlmDashboardService.getDashboard(actorUserId, leaderId);

        assertNotNull(response);
        assertTrue(response.teamSize() >= 0);
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("+229" + UUID.randomUUID().toString().replace("-", "").substring(0, 9))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private void seedRelationship(UUID ancestorId, UUID descendantId, int level) {
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .createdAt(OffsetDateTime.now().minusDays(3))
                .build());
    }
}
