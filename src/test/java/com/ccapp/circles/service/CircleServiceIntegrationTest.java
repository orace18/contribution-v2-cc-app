package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.dto.CircleCreatorRole;
import com.ccapp.circles.dto.CircleParticipantsResponse;
import com.ccapp.circles.dto.CircleResponse;
import com.ccapp.circles.dto.CreateCircleRequest;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.verification.service.PhoneVerificationService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CircleService.class, TestJacksonAndCachingConfig.class})
class CircleServiceIntegrationTest {

    @Autowired
    private CircleService circleService;
    @Autowired
    private CircleMemberRepository circleMemberRepository;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;

    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private CountryContextService countryContextService;
    @MockBean
    private DelegationService delegationService;
    @MockBean
    private DelegatedActionService delegatedActionService;
    @MockBean
    private PhoneVerificationService phoneVerificationService;
    @MockBean
    private CountryConfigResolver countryConfigResolver;

    @BeforeEach
    void setUp() {
        when(countryContextService.resolve(any())).thenAnswer(invocation -> new CountryContext("BJ", "BJ", "XOF"));
        when(countryConfigResolver.resolveActiveConfig(any(), any())).thenAnswer(invocation -> new CountryConfigResolver.ResolvedConfig(
                invocation.getArgument(0),
                invocation.getArgument(1),
                1,
                "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        ));
        when(countryConfigResolver.payloadAsObjectNode(any(CountryConfigResolver.ResolvedConfig.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode());
    }

    @Test
    void createCircle_shouldAttachCoachAndAdvisorsFromMlmHierarchy() {
        UUID creatorId = createAccountHolder("Creator", "Member");
        UUID sponsorId = createAccountHolder("Sponsor", "Coach");
        UUID sponsorOfSponsorId = createAccountHolder("Sponsor", "AdvisorOne");
        UUID sponsorOfSponsorOfSponsorId = createAccountHolder("Sponsor", "AdvisorTwo");
        createUserMapping(creatorId, AccountStatus.ACTIVE);

        seedRelationship(creatorId, creatorId, 0);
        seedRelationship(sponsorId, creatorId, 1);
        seedRelationship(sponsorOfSponsorId, creatorId, 2);
        seedRelationship(sponsorOfSponsorOfSponsorId, creatorId, 3);

        CircleResponse response = circleService.createCircle(
                creatorId,
                new CreateCircleRequest("Les Batisseurs", CircleCreatorRole.MEMBER)
        );

        assertNotNull(response.id());
        assertEquals(sponsorId, response.coachId());
        assertEquals(1, response.membersCount());

        List<CircleMember> participants = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(response.id());
        assertEquals(4, participants.size());

        CircleMember creatorMember = participants.stream()
                .filter(member -> member.getAccountHolderId().equals(creatorId))
                .findFirst()
                .orElseThrow();
        assertEquals(CircleMemberRole.MEMBER, creatorMember.getRole());
        assertTrue(creatorMember.isFinancialMember());

        CircleMember coach = participants.stream()
                .filter(member -> member.getAccountHolderId().equals(sponsorId))
                .findFirst()
                .orElseThrow();
        assertEquals(CircleMemberRole.COACH, coach.getRole());
        assertFalse(coach.isFinancialMember());

        CircleParticipantsResponse grouped = circleService.getParticipants(response.id());
        assertEquals(1, grouped.members().size());
        assertNotNull(grouped.coach());
        assertEquals(sponsorId, grouped.coach().accountHolderId());
        assertEquals(2, grouped.advisors().size());

        var persistedCircle = circleRepository.findById(response.id()).orElseThrow();
        assertNotNull(persistedCircle.getConfigSnapshot());
        assertEquals(1, persistedCircle.getConfigVersion());
    }

    @Test
    void createCircle_shouldRejectWhenCreatorIsNotActive() {
        UUID creatorId = createAccountHolder("Creator", "Inactive");
        UUID sponsorId = createAccountHolder("Sponsor", "Coach");
        createUserMapping(creatorId, AccountStatus.SUSPENDED);

        seedRelationship(creatorId, creatorId, 0);
        seedRelationship(sponsorId, creatorId, 1);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleService.createCircle(creatorId, new CreateCircleRequest("Inactive Circle", CircleCreatorRole.MEMBER))
        );

        assertEquals("MEMBER_NOT_ACTIVE", ex.getMessage());
    }

    @Test
    void createCircle_shouldRejectWhenSponsorLevelOneIsMissing() {
        UUID creatorId = createAccountHolder("Creator", "NoSponsor");
        UUID orphanAncestorId = createAccountHolder("Orphan", "Ancestor");
        createUserMapping(creatorId, AccountStatus.ACTIVE);

        seedRelationship(creatorId, creatorId, 0);
        seedRelationship(orphanAncestorId, creatorId, 2);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleService.createCircle(creatorId, new CreateCircleRequest("No Sponsor Circle", CircleCreatorRole.MEMBER))
        );

        assertEquals("SPONSOR_NOT_FOUND", ex.getMessage());
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

    private void createUserMapping(UUID accountHolderId, AccountStatus status) {
        User user = userRepository.save(User.builder()
                .firebaseUid("uid-" + UUID.randomUUID())
                .email("user+" + UUID.randomUUID() + "@dev.local")
                .role(UserRole.USER)
                .accountStatus(status)
                .kycStatus(KycStatus.NOT_STARTED)
                .build());
        appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .accountHolderId(accountHolderId)
                .role(UserRole.USER)
                .build());
    }

    private void seedRelationship(UUID ancestorId, UUID descendantId, int level) {
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(ancestorId)
                .descendantId(descendantId)
                .level(level)
                .build());
    }
}
