package com.ccapp.mlm.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AutoPlacementService.class)
class AutoPlacementServiceIntegrationTest {

    @Autowired
    private AutoPlacementService autoPlacementService;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @Test
    void resolvePlacementParent_shouldFollowBreadthFirstChronologicalOrder() {
        UUID sponsor = createAccountHolder("Sponsor", "Root");
        seed(sponsor, sponsor, 0, OffsetDateTime.now().minusDays(10));

        List<UUID> levelOne = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID child = createAccountHolder("L1", "Child" + i);
            levelOne.add(child);
            seed(child, child, 0, OffsetDateTime.now().minusDays(9));
            seed(sponsor, child, 1, OffsetDateTime.now().minusDays(8).plusMinutes(i));
        }

        UUID candidate1 = autoPlacementService.resolvePlacementParent(sponsor, 5);
        assertEquals(levelOne.get(0), candidate1);

        UUID oldestLevelOne = levelOne.get(0);
        for (int i = 0; i < 5; i++) {
            UUID leaf = createAccountHolder("Leaf", "Node" + i);
            seed(leaf, leaf, 0, OffsetDateTime.now().minusDays(7));
            seed(oldestLevelOne, leaf, 1, OffsetDateTime.now().minusDays(6).plusMinutes(i));
        }

        UUID candidate2 = autoPlacementService.resolvePlacementParent(sponsor, 5);
        assertEquals(levelOne.get(1), candidate2);
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private void seed(UUID ancestorId, UUID descendantId, int level, OffsetDateTime createdAt) {
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .createdAt(createdAt)
                .build());
    }
}
