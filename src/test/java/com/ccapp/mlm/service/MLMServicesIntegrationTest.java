package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.dto.MlmPlacementResponse;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({MLMPlacementService.class, MLMQueryService.class, AutoPlacementService.class})
class MLMServicesIntegrationTest {

    @Autowired
    private MLMPlacementService mlmPlacementService;
    @Autowired
    private MLMQueryService mlmQueryService;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private FinancialAuditService financialAuditService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void placementAndQueries_shouldRespectMaxDirectChildrenAndSpillover() {
        UUID actorUserId = UUID.randomUUID();
        UUID sponsorId = createAccountHolder("Sponsor", "Root");
        UUID child1 = createAccountHolder("Child", "One");
        UUID child2 = createAccountHolder("Child", "Two");
        UUID child3 = createAccountHolder("Child", "Three");
        UUID child4 = createAccountHolder("Child", "Four");
        UUID child5 = createAccountHolder("Child", "Five");
        UUID spillover = createAccountHolder("Child", "Spill");

        mlmPlacementService.placeAccountHolder(actorUserId, child1, sponsorId);
        mlmPlacementService.placeAccountHolder(actorUserId, child2, sponsorId);
        mlmPlacementService.placeAccountHolder(actorUserId, child3, sponsorId);
        mlmPlacementService.placeAccountHolder(actorUserId, child4, sponsorId);
        mlmPlacementService.placeAccountHolder(actorUserId, child5, sponsorId);

        MlmPlacementResponse spilloverPlacement = mlmPlacementService.placeAccountHolder(actorUserId, spillover, sponsorId);
        assertEquals(child1, spilloverPlacement.placedUnderAccountHolderId());
        assertEquals(5L, mlmRelationshipRepository.countByAncestorIdAndLevel(sponsorId, 1));
        assertTrue(mlmRelationshipRepository.existsByAncestorIdAndDescendantId(sponsorId, spillover));

        var upline = mlmQueryService.getUpline(actorUserId, spillover);
        assertEquals(2, upline.size());
        assertEquals(child1, upline.get(0).accountHolderId());
        assertEquals(1, upline.get(0).level());
        assertEquals(sponsorId, upline.get(1).accountHolderId());
        assertEquals(2, upline.get(1).level());

        var downline = mlmQueryService.getDownline(actorUserId, sponsorId);
        assertEquals(6, downline.size());

        var teamSize = mlmQueryService.getTeamSize(actorUserId, sponsorId);
        assertEquals(6L, teamSize.teamSize());

        var levelOneMembers = mlmQueryService.getLevelMembers(actorUserId, sponsorId, 1);
        assertEquals(5, levelOneMembers.size());
        List<UUID> levelOneIds = levelOneMembers.stream().map(member -> member.accountHolderId()).toList();
        assertTrue(levelOneIds.containsAll(List.of(child1, child2, child3, child4, child5)));

        Mockito.verify(domainEventPublisher, Mockito.atLeast(6)).publish(any());
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
        assertNotNull(saved.getId());
        return saved.getId();
    }
}
